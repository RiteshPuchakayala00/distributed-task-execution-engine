# Senior Java Engineer Interview Guide: Distributed Task Execution Engine

This document contains a rigorous technical interview simulation, assuming you designed and implemented the Distributed Task Execution Engine. 

---

## Part 1: System Architecture & Trade-offs

### Q1: Can you walk me through the high-level architecture of the Distributed Task Execution Engine you built? What were the primary design drivers?
*   **Ideal Answer**: "I designed a Master-Worker topology where a central Master coordinates task distribution, and multiple stateless Workers execute the tasks. The primary driver was building a resilient, concurrent system using pure Core Java without external frameworks. The Master utilizes a push-based scheduling model, dispatching tasks via custom TCP sockets with length-prefixed framing. To ensure fault tolerance, I implemented a heartbeat mechanism. If a worker goes silent, the master requeues its tasks. I relied on lock-free data structures like `ConcurrentHashMap` and CAS operations to avoid thread contention on the master."
*   **Bad Answer**: "It's a client-server app. The master sends tasks to workers using sockets and waits for them to finish." *(Too simplistic, lacks architectural depth, misses fault tolerance and concurrency).*
*   **Common Mistakes**: Failing to mention the trade-offs (e.g., single point of failure on the Master), forgetting to mention how state is managed (in-memory), or not mentioning the push-based vs pull-based scheduling decision.
*   **Follow-up Questions**: Why a push-based model instead of having workers pull tasks? How does the master handle backpressure?
*   **Difficulty Level**: Medium

### Q2: You mentioned the Master node holds all tasks in memory. What happens to the pending and running tasks if the Master process crashes?
*   **Ideal Answer**: "Currently, they are lost. The architecture trades durability for extremely high throughput and simplicity by relying purely on in-memory structures like `PriorityBlockingQueue`. In a true production environment requiring high availability, I would introduce a Write-Ahead Log (WAL) or an embedded disk-backed store (like RocksDB) to persist task states across restarts. Additionally, implementing Raft for Master-Master replication would eliminate the Single Point of Failure."
*   **Bad Answer**: "The system just restarts and the tasks are gone. There's nothing we can do about it."
*   **Common Mistakes**: Defending the lack of persistence as a "feature" rather than acknowledging it as a strict architectural trade-off. 
*   **Follow-up Questions**: How exactly would you implement a WAL in this codebase without third-party libraries?
*   **Difficulty Level**: Hard

### Q3: Why did you choose raw TCP Sockets instead of HTTP/REST or gRPC?
*   **Ideal Answer**: "The constraint was to use only Core Java. Building an HTTP server from scratch using raw sockets or `com.sun.net.httpserver` adds unnecessary overhead for a strictly internal machine-to-machine system. Raw TCP allows for a highly optimized, low-latency, persistent bidirectional connection. It avoids the overhead of HTTP headers and connection setup/teardown per request. I implemented length-prefixed framing to solve the TCP streaming boundary problem."
*   **Bad Answer**: "Because sockets are faster than HTTP." *(True, but lacks depth and doesn't explain the framing problem).*
*   **Common Mistakes**: Failing to mention TCP stream framing (the fact that TCP is a byte stream and boundaries must be manually handled).
*   **Follow-up Questions**: How do you prevent a slow-reading worker from blocking the Master's writer thread?
*   **Difficulty Level**: Medium

### Q4: Explain the difference between Push-based and Pull-based task scheduling. Why did you choose Push?
*   **Ideal Answer**: "In Pull-based, workers request work when idle. It simplifies load balancing but introduces polling latency and overhead. In Push-based, the Master actively assigns work. I chose Push to minimize latency; tasks are dispatched the millisecond a worker has capacity. To prevent overwhelming a worker (the main drawback of Push), I implemented a load-tracking mechanism in the `WorkerRegistry` where tasks are only pushed until `currentLoad == availableCores`."
*   **Bad Answer**: "Push is better because the master is in control."
*   **Common Mistakes**: Not addressing the backpressure problem inherent in Push-based systems.
*   **Follow-up Questions**: How does your implementation handle a worker that accepts tasks but executes them extremely slowly (straggler)?
*   **Difficulty Level**: Hard

### Q5: How does your system prevent Out-Of-Memory (OOM) errors on the Master node if a client submits millions of tasks?
*   **Ideal Answer**: "The `TaskManager` uses a bounded `PriorityBlockingQueue`. When the queue reaches `queueCapacity`, the `submitTask` method rejects new tasks, applying backpressure to the client. Secondly, I implemented an O(1) TTL sweeper using a `DelayQueue`. When tasks reach a terminal state, they are added to the `DelayQueue`. A scheduled thread polls this queue and removes tasks from the `ConcurrentHashMap` once their TTL expires, preventing memory leaks."
*   **Bad Answer**: "Java's Garbage Collector handles it."
*   **Common Mistakes**: Assuming the JVM will magically page memory to disk, or forgetting that `ConcurrentHashMap` holds strong references that prevent GC.
*   **Follow-up Questions**: What happens to the client when a task is rejected? How should the client handle it?
*   **Difficulty Level**: Medium

### Q6: If you were to deploy this across multiple AWS availability zones, what specific network issues would your custom TCP protocol face?
*   **Ideal Answer**: "Cross-AZ traffic introduces higher latency and potential packet loss. The biggest risk is silent connection drops (TCP half-open connections) caused by NAT gateways or firewalls dropping idle connections. My protocol mitigates this via the `HEARTBEAT` messages sent every 2 seconds, which act as TCP keep-alives. Additionally, the plaintext TCP traffic would be a security risk across AZs, so I would need to wrap the sockets in `SSLSocket` with mTLS."
*   **Bad Answer**: "It would work exactly the same, AWS handles the network."
*   **Common Mistakes**: Ignoring the existence of middleboxes (firewalls, NATs) that silently terminate idle TCP connections.
*   **Follow-up Questions**: How does your `WorkerClient` detect a half-open connection if it's currently blocking on `InputStream.read()`?
*   **Difficulty Level**: Expert

### Q7: Explain your load balancing algorithm inside `TaskScheduler`.
*   **Ideal Answer**: "The scheduler relies on a load-factor calculation: `Active Tasks / Available Cores`. The `WorkerRegistry` provides a list of workers. I sort this list based on the lowest load factor. The scheduler then assigns tasks to the least loaded worker until its active tasks equal its core count. This ensures optimal utilization of multi-core worker nodes while preventing any single node from queuing tasks internally while others are idle."
*   **Bad Answer**: "It just picks a worker that is marked IDLE."
*   **Common Mistakes**: Failing to explain how the Master *knows* how many cores the worker has (it's passed during the `REGISTER_REQUEST` handshake).
*   **Follow-up Questions**: Sorting the worker list is O(N log N). If you have 10,000 workers, this sorting happens very frequently. How would you optimize this?
*   **Difficulty Level**: Hard

### Q8: What is the "Thundering Herd" problem, and does your system suffer from it?
*   **Ideal Answer**: "The Thundering Herd occurs when a large number of processes wake up simultaneously upon an event, but only one can process it, causing a massive spike in CPU/Network. My system doesn't suffer from it on task assignment because the Master pushes tasks individually to specific workers. However, if the Master crashes and restarts, all 1,000 workers might try to reconnect simultaneously, overwhelming the Master's `ServerSocket.accept()`. I mitigated this in `WorkerClient` by implementing randomized exponential backoff during reconnections."
*   **Bad Answer**: "Thundering herd is when too many tasks are submitted. Yes, we bound the queue."
*   **Common Mistakes**: Confusing Thundering Herd with standard high load. It specifically refers to synchronized waking of multiple waiting entities.
*   **Follow-up Questions**: Show me exactly how you would write the math for a randomized exponential backoff algorithm.
*   **Difficulty Level**: Expert

### Q9: How did you design the system to respect the Single Responsibility Principle (SRP)?
*   **Ideal Answer**: "I strictly separated concerns. For example, network I/O is handled by `WorkerConnectionHandler`, byte-level framing is handled by `MessageCodec`, object mapping by `SecureObjectInputStream`, and business logic by `TaskManager` and `TaskScheduler`. The `WorkerRegistry` only cares about state, not what the state means. Bootstrapping and dependency injection are isolated to the `MasterBootstrap` class, meaning my business components are completely decoupled from network instantiation."
*   **Bad Answer**: "I put everything in different packages."
*   **Common Mistakes**: Providing generic answers without naming specific classes in the architecture that demonstrate the separation.
*   **Follow-up Questions**: `WorkerConnectionHandler` both reads from the socket and calls `taskManager.completeTask()`. Does this violate SRP?
*   **Difficulty Level**: Medium

### Q10: You opted out of using a DI framework like Spring. How does your application manage dependencies and lifecycles?
*   **Ideal Answer**: "I utilized Pure Dependency Injection (also known as Manual DI). The `Bootstrap` classes act as the Composition Root. They instantiate all the singletons (`TaskManager`, `WorkerRegistry`, `TaskScheduler`) and pass them via constructor injection to the dependent classes. For lifecycles, I utilized `Runtime.getRuntime().addShutdownHook()` to trigger a graceful shutdown sequence, which explicitly calls `.close()` on thread pools and network managers."
*   **Bad Answer**: "I just used the `new` keyword wherever I needed a class."
*   **Common Mistakes**: Creating tight coupling by using `new` inside business logic constructors, making unit testing via mocks impossible. 
*   **Follow-up Questions**: What is the danger of doing heavy I/O operations inside a JVM shutdown hook?
*   **Difficulty Level**: Medium

---
*Note: This is Batch 1 containing Questions 1-10. Reply "Continue" to generate the next batch covering TCP Networking, Serialization, and Concurrency.*
