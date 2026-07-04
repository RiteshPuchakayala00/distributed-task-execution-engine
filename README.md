# Distributed Task Execution Engine

![Java 21](https://img.shields.io/badge/Java-21-blue.svg)
![Build](https://img.shields.io/badge/build-passing-brightgreen.svg)
![License](https://img.shields.io/badge/license-MIT-green.svg)

A highly resilient, multi-threaded, master-worker distributed task execution engine built entirely in **Core Java 21**. This system was designed with strict adherence to SOLID principles, requiring **zero external dependencies** (no Spring, no Netty, no Kafka).

## Features

- **True Core-Aware Load Balancing**: Tasks are optimally scheduled based on dynamic worker loads and available CPU cores.
- **Resilient Networking**: Custom TCP framing protocol with robust heartbeat monitoring and automatic reconnection logic.
- **Memory Safety**: Bounded task queues and O(1) TTL memory sweeps using `DelayQueue` to prevent Out-Of-Memory (OOM) errors.
- **Secure Serialization**: Hardened custom `SecureObjectInputStream` that whitelists deserialization, blocking Remote Code Execution (RCE) vulnerabilities.
- **Concurrency Masterclass**: Zero lock-contention data structures (`ConcurrentHashMap`, `PriorityBlockingQueue`) and strict atomic state transitions via CAS.

## Project Structure

```text
distributed-task-engine/
├── common/
│   └── src/main/java/com/engine/common/      # Core models, protocols, secure serialization
├── master/
│   └── src/main/java/com/engine/master/      # Task scheduling, worker registry, recovery
├── worker/
│   └── src/main/java/com/engine/worker/      # Task execution, heartbeat threads, networking
└── pom.xml                                   # Root Maven reactor
```

## Setup Guide

### Prerequisites
- Java 21 JDK
- Maven 3.9+

### Build the Project
Navigate to the root directory and run:
```bash
mvn clean install
```
This will compile all modules, run the comprehensive 137-test suite, and package the JARs.

## How to Run

### 1. Start the Master Node
The master node binds to `localhost:9090` by default.
```bash
java -jar master/target/engine-master-1.0.0-SNAPSHOT.jar
```

### 2. Start Worker Nodes
You can start as many worker nodes as you like. They will automatically connect to the master node and register their available CPU cores.
```bash
java -jar worker/target/engine-worker-1.0.0-SNAPSHOT.jar
```

## Screenshots

*(Placeholder for Dashboard UI or Terminal Multiplexer view)*
> `[Insert Master Terminal Screenshot Here]`
> `[Insert Worker Terminal Screenshot Here]`

## Sample Logs

**Master Node (Load Balancing in Action):**
```text
[INFO ] [main] [MasterBootstrap] Starting Master Server on port 9090
[INFO ] [worker-handler-w1] [WorkerRegistry] Registered worker: workerId=w1, hostname=worker-host, cores=8
[INFO ] [TaskManager] Submitted task req-192 with priority HIGH
[DEBUG] [TaskScheduler] Assigned task req-192 to worker w1 (Load: 1/8)
[INFO ] [TaskManager] Task req-192 completed with status: COMPLETED
[INFO ] [TaskManager] Swept 12 terminal tasks from memory in O(1) time
```

**Worker Node (Task Execution & Backoff):**
```text
[INFO ] [main] [WorkerBootstrap] Worker starting... (8 available cores)
[INFO ] [Thread-1] [WorkerClient] Registered successfully with ID: w1
[INFO ] [TaskRunner] Executing task: req-192
[INFO ] [TaskRunner] Task req-192 completed successfully
[WARN ] [ReconnectHandler] Reconnection attempt 1 failed: Connection refused
[INFO ] [ReconnectHandler] Successfully reconnected to master on attempt 2
```

## Configuration

Both Master and Worker behavior can be tuned via `application.properties` (or environment variables).

**Master Options:**
- `port`: TCP port to bind (default: 9090)
- `queue.capacity`: Maximum pending tasks before backpressure (default: 10000)
- `heartbeat.dead.ms`: Time before a worker is marked DEAD (default: 10000)

**Worker Options:**
- `master.host`: Master IP address
- `master.port`: Master port
- `worker.cores`: Override CPU core detection (default: `Runtime.getRuntime().availableProcessors()`)

## Testing
The project includes a massive suite of unit and integration tests covering concurrency edge cases, serialization attacks, and network failures.

```bash
mvn test
```

## Future Improvements
- **State Persistence**: Introduce an embedded WAL (Write-Ahead Log) to persist the Master's task queue across restarts.
- **mTLS Security**: Upgrade the TCP plain-text sockets to use `SSLSocket` with mutual TLS for Zero-Trust environments.
- **High Availability (HA) Master**: Implement Raft consensus for active-active master scaling.

## Contribution Guide
1. Fork the repository.
2. Create a feature branch (`git checkout -b feature/amazing-feature`).
3. Ensure you do not add any external frameworks (Spring, Guava, etc.) to the `pom.xml`. This project strictly uses Core Java.
4. Ensure all new logic is fully tested (`mvn clean test`).
5. Open a Pull Request.

## License
This project is licensed under the MIT License - see the LICENSE file for details.
