# Distributed Task Engine Architecture

This document provides a deep dive into the internal architecture, network communication, and fault-tolerance mechanisms of the Distributed Task Execution Engine.

---

## 1. High-Level Architecture

The system follows a classic Master-Worker topology where a single Master node acts as the coordinator and multiple Worker nodes execute tasks in parallel.

```mermaid
graph TD
    Client[Client / Job Submitter] -->|Submits Task| M(Master Node)
    
    subgraph Master
        TS(Task Scheduler)
        TM(Task Manager)
        WR(Worker Registry)
        HM(Heartbeat Monitor)
    end
    
    M -->|Assigns Task| W1[Worker Node 1]
    M -->|Assigns Task| W2[Worker Node 2]
    M -->|Assigns Task| WN[Worker Node N]
    
    subgraph Worker 1
        TE1(Task Executor)
        HM1(Heartbeat Thread)
    end
    
    subgraph Worker 2
        TE2(Task Executor)
        HM2(Heartbeat Thread)
    end
```

---

## 2. Communication Protocol

All communication between the Master and Workers occurs over long-lived TCP sockets using a custom binary framing protocol. 

**Wire Format:**
*   `[Length Prefix (4 Bytes)]` — Indicates the length of the serialized payload.
*   `[Payload (N Bytes)]` — The serialized `Message` object.

This length-prefix framing prevents the system from being vulnerable to Out-Of-Memory (OOM) attacks from malicious clients, as the `MessageCodec` enforces a strict 10MB `MAX_MESSAGE_SIZE_BYTES` limit before allocating heap memory.

---

## 3. Worker Registration & Initial Handshake

When a worker starts, it initiates a TCP connection to the master and attempts to register itself by broadcasting its hardware capabilities (e.g., available CPU cores).

```mermaid
sequenceDiagram
    participant Worker
    participant Master
    
    Worker->>Master: Connect TCP
    Worker->>Master: REGISTER_REQUEST (cores, hostname)
    Master-->>Worker: REGISTER_ACK (assigned workerId)
    
    Note over Worker,Master: Connection Established
    
    loop Every 2 seconds
        Worker->>Master: HEARTBEAT
        Master-->>Worker: HEARTBEAT_ACK
    end
```

---

## 4. Task Scheduling Lifecycle

The Master utilizes a push-based scheduling model. Instead of workers pulling tasks, the `TaskScheduler` runs on a dedicated thread and actively balances pending tasks across available workers using a load-factor metric `(Current Tasks / Available Cores)`.

```mermaid
sequenceDiagram
    participant Client
    participant TaskManager
    participant TaskScheduler
    participant WorkerRegistry
    participant Worker
    
    Client->>TaskManager: submitTask(Task)
    TaskManager->>TaskManager: Enqueue to PriorityQueue
    
    TaskScheduler->>TaskManager: pollPendingTask()
    TaskScheduler->>WorkerRegistry: getAvailableWorkers()
    WorkerRegistry-->>TaskScheduler: List<WorkerInfo> (sorted by load)
    
    TaskScheduler->>Worker: TASK_ASSIGN (Task)
    Worker-->>Master: TASK_ACK
    
    Worker->>Worker: Execute Task Logic
    Worker->>Master: TASK_RESULT
    Master->>TaskManager: completeTask(Result)
```

---

## 5. Heartbeat & Failure Recovery

Resilience is guaranteed through continuous heartbeat monitoring. If a worker fails to send a heartbeat within the designated `deadThresholdMs`, the master reclaims its active tasks.

```mermaid
stateDiagram-v2
    [*] --> IDLE: Registration Success
    
    IDLE --> BUSY: Load == Cores
    BUSY --> IDLE: Load < Cores
    
    IDLE --> UNRESPONSIVE: Missed Heartbeats (Warn)
    BUSY --> UNRESPONSIVE: Missed Heartbeats (Warn)
    
    UNRESPONSIVE --> IDLE: Heartbeat Resumed
    UNRESPONSIVE --> DEAD: Timeout Exceeded (Fatal)
    
    DEAD --> [*]: Tasks Requeued
```

### 5.1 Worker Failure Redistribution

When a worker is marked `DEAD`, the `FailureRecoveryManager` immediately redistributes its active workload.

```mermaid
sequenceDiagram
    participant HeartbeatMonitor
    participant FailureRecoveryManager
    participant TaskManager
    participant TaskScheduler
    
    HeartbeatMonitor->>HeartbeatMonitor: Detect Missed Heartbeat > 10s
    HeartbeatMonitor->>WorkerRegistry: updateStatus(DEAD)
    HeartbeatMonitor->>FailureRecoveryManager: handleWorkerFailure(workerId)
    
    FailureRecoveryManager->>TaskManager: getTasksAssignedToWorker(workerId)
    TaskManager-->>FailureRecoveryManager: List<Task>
    
    loop For Each Task
        FailureRecoveryManager->>TaskManager: requeueTask(Task)
        TaskManager->>TaskManager: Increment retryCount
        TaskManager->>TaskManager: Offer to PriorityQueue
    end
    
    Note over TaskScheduler,TaskManager: Scheduler will eventually<br/>assign requeued tasks to<br/>healthy workers.
```

---

## 6. Secure Serialization

The Engine prevents classic Java deserialization attacks (like Arbitrary Code Execution) via a custom `SecureObjectInputStream`.

By overriding `resolveClass()`, the stream strictly whitelists valid package namespaces.
*   `com.engine.*` (Internal models)
*   `java.lang.*` (Primitives/Strings)
*   `java.util.*` (Collections)

Any unknown payload sent by a compromised worker is instantly blocked with a `SerializationException`.
