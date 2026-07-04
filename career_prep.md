# Career Prep: Distributed Task Execution Engine

## 1. Hiring Manager Perspectives

### ☁️ Amazon (AWS)
**What they look for:** Distributed systems at scale, handling failure, low-level networking, and Leadership Principles (Bias for Action, Dive Deep).
**Review:** "I love that you built this without Spring Boot or Netty. It shows you 'Dove Deep' into how TCP actually works and how to manage raw sockets. The heartbeat monitor and failure recovery mechanisms demonstrate you understand that in distributed systems, everything fails all the time. Your bounded queue and backpressure handling show mature operational awareness."

### 🏢 Microsoft (Azure / Core OS)
**What they look for:** Concurrency mastery, thread safety, system-level design, and robust architecture.
**Review:** "Using `DelayQueue` for O(1) TTL sweeping and `compareAndSet` (CAS) for atomic state transitions shows a senior-level understanding of the JVM concurrency model. You avoided naive synchronization bottlenecks, which is exactly what we need for high-performance platform engineering."

### 🔍 Google (Infra / Cloud)
**What they look for:** Algorithmic efficiency, Big-O awareness, data structure choice, and custom implementations.
**Review:** "Google loves engineers who don't just glue APIs together. Building your own binary framing protocol to solve TCP stream boundaries is impressive. Moving from an O(N) sweep to O(1) via `DelayQueue` demonstrates the algorithmic thinking we test for in our interviews."

### 🛡️ JPMorgan (Trading / Core Infra)
**What they look for:** Security, low-latency, strict typing, reliability, and no-framework constraints.
**Review:** "Financial institutions are notoriously strict about external dependencies due to security risks. Building this strictly in Core Java 21 is a huge plus. Furthermore, implementing a `SecureObjectInputStream` whitelist to mitigate Java's notorious RCE serialization vulnerabilities shows enterprise-grade security awareness."

---

## 2. Resume Bullet Points (Maximizing Impact)

*Use 3-4 of these depending on the job you are applying for.*

*   **Designed and implemented** a Master-Worker distributed task execution engine from scratch in pure Core Java 21, handling task orchestration, network routing, and load balancing without third-party frameworks.
*   **Engineered a custom TCP framing protocol** over raw sockets, utilizing length-prefixed binary encoding to ensure reliable data transmission and prevent stream fragmentation across distributed nodes.
*   **Optimized cluster utilization** by implementing a push-based load balancer that dynamically routes tasks to worker nodes based on real-time CPU core availability and current active load.
*   **Achieved lock-free concurrency** on the Master node by leveraging `ConcurrentHashMap`, `PriorityBlockingQueue`, and Atomic CAS (Compare-And-Swap) operations, eliminating thread-contention bottlenecks.
*   **Secured inter-node communication** by overriding native Java serialization with a strict class-whitelisting `SecureObjectInputStream`, mitigating Remote Code Execution (RCE) vulnerabilities.
*   **Built robust fault tolerance** featuring a scheduled heartbeat monitor, automatic failure detection, task requeuing logic, and randomized exponential backoff for worker reconnections.
*   **Prevented Out-Of-Memory (OOM) failures** by implementing a bounded priority queue for backpressure and a `DelayQueue`-backed TTL sweeper that reaps terminal tasks in O(1) time.
*   **Delivered a production-ready codebase** backed by a 130+ suite of unit and integration tests using JUnit 5 and Mockito, ensuring 100% build reliability across multi-threaded scenarios.

---

## 3. The 30-Second Elevator Pitch
"I built a Master-Worker distributed task execution engine strictly using Core Java 21, avoiding all external frameworks like Spring or Netty. It coordinates thousands of tasks across distributed worker nodes using a custom TCP framing protocol over raw sockets. To maximize throughput, I implemented a push-based load balancer that dynamically assigns tasks based on worker CPU availability, and I heavily utilized lock-free data structures and CAS operations to ensure the master node remains entirely non-blocking. It’s fully fault-tolerant, handling worker crashes through continuous heartbeat monitoring and automatic task requeuing."

---

## 4. The 2-Minute Project Explanation (For Recruiter Screens)

"For my recent capstone project, I wanted to really dive deep into distributed systems and concurrency, rather than just gluing together REST APIs. So, I built a Distributed Task Execution Engine from the ground up using only Core Java 21. 

The architecture is a Master-Worker topology. Clients submit tasks to the Master, which maintains a bounded Priority Queue. Instead of having workers pull tasks, the Master runs a load-aware scheduler that pushes tasks to workers over raw TCP sockets. Because I didn't use Netty, I had to solve TCP streaming boundaries myself by writing a custom length-prefixed binary codec. 

One of the biggest challenges was concurrency. The Master node processes heartbeats, task results, and incoming tasks simultaneously. To prevent thread bottlenecks, I completely avoided `synchronized` blocks, relying instead on `ConcurrentHashMap` and atomic Compare-And-Swap (CAS) operations for state transitions.

I also focused heavily on fault tolerance and security. If a worker crashes, a heartbeat monitor detects the timeout and automatically requeues its assigned tasks. To protect against Java serialization attacks, I built a hardened `SecureObjectInputStream` that strictly whitelists permitted classes. Finally, I ensured memory safety by using a `DelayQueue` to sweep completed tasks from memory in O(1) time. The entire system is validated by over 130 tests."

---

## 5. Deep Technical Explanation (For Technical Interviews)

**Interviewer:** *“Tell me about the most technically challenging part of your Distributed Task Engine.”*

"The most complex engineering challenge was managing the shared state on the Master node without introducing lock contention, specifically dealing with the **Check-Then-Act race conditions** between the `HeartbeatMonitor` and the `TaskScheduler`. 

Initially, I had a classic race condition: The Scheduler would read a worker's status as `IDLE`, decide to assign a task, but right before the assignment, the `HeartbeatMonitor` would detect a timeout and mark that exact worker as `DEAD`. If the assignment went through, the task would be sent to a dead socket and lost.

I couldn't just throw a `synchronized` block over the worker registry, as that would serialize all network I/O and destroy throughput. 

**The Solution:**
I redesigned the `WorkerInfo` state machine to use `AtomicReference<WorkerStatus>`. Instead of simple setters, I exposed a `compareAndSetStatus(expected, new)` method. 

When the Scheduler wants to assign a task, it attempts a CAS operation to transition the worker from `IDLE` to `BUSY`. If the `HeartbeatMonitor` had already transitioned it to `DEAD`, the CAS fails instantly. The Scheduler detects the failure, gracefully aborts the assignment, and moves to the next available worker. 

This lock-free approach guaranteed absolute data consistency between the background monitoring threads and the active scheduling threads, while allowing the Master to scale to handle thousands of concurrent socket connections without thread starvation."
