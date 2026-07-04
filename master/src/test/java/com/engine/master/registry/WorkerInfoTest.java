package com.engine.master.registry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("WorkerInfo — worker metadata and lifecycle")
class WorkerInfoTest {

    private static final String WORKER_ID = "worker-1";
    private static final String HOSTNAME = "node1.cluster.local";
    private static final int PORT = 8080;
    private static final int CORES = 4;

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("Constructor sets all fields correctly")
        void test_WhenValidArgs_ThenAllFieldsSet() {
            WorkerInfo info = new WorkerInfo(WORKER_ID, HOSTNAME, PORT, CORES);

            assertAll("constructor fields",
                () -> assertEquals(WORKER_ID, info.getWorkerId()),
                () -> assertEquals(HOSTNAME, info.getHostname()),
                () -> assertEquals(PORT, info.getPort()),
                () -> assertEquals(CORES, info.getAvailableCores()),
                () -> assertNotNull(info.getRegisteredAt(),
                        "registeredAt should be set on construction")
            );
        }

        @Test
        @DisplayName("Null workerId throws NullPointerException")
        void test_WhenNullWorkerId_ThenThrowsNPE() {
            assertThrows(NullPointerException.class,
                () -> new WorkerInfo(null, HOSTNAME, PORT, CORES),
                "null workerId must be rejected");
        }

        @Test
        @DisplayName("Null hostname throws NullPointerException")
        void test_WhenNullHostname_ThenThrowsNPE() {
            assertThrows(NullPointerException.class,
                () -> new WorkerInfo(WORKER_ID, null, PORT, CORES),
                "null hostname must be rejected");
        }

        @Test
        @DisplayName("Zero availableCores throws IllegalArgumentException")
        void test_WhenZeroCores_ThenThrowsIAE() {
            assertThrows(IllegalArgumentException.class,
                () -> new WorkerInfo(WORKER_ID, HOSTNAME, PORT, 0),
                "0 cores must be rejected");
        }

        @Test
        @DisplayName("Negative availableCores throws IllegalArgumentException")
        void test_WhenNegativeCores_ThenThrowsIAE() {
            assertThrows(IllegalArgumentException.class,
                () -> new WorkerInfo(WORKER_ID, HOSTNAME, PORT, -2),
                "negative cores must be rejected");
        }
    }

    @Nested
    @DisplayName("Status management")
    class StatusManagement {

        private WorkerInfo worker;

        @BeforeEach
        void setUp() {
            worker = new WorkerInfo(WORKER_ID, HOSTNAME, PORT, CORES);
        }

        @Test
        @DisplayName("Default status is IDLE")
        void test_WhenNewlyCreated_ThenStatusIsIdle() {
            assertEquals(WorkerStatus.IDLE, worker.getStatus(),
                    "new worker should start as IDLE");
        }

        @Test
        @DisplayName("setStatus changes status")
        void test_WhenSetStatus_ThenStatusChanges() {
            worker.setStatus(WorkerStatus.BUSY);
            assertEquals(WorkerStatus.BUSY, worker.getStatus());
        }

        @Test
        @DisplayName("isAvailable delegates to status.isAvailable")
        void test_WhenStatusChanges_ThenIsAvailableReflectsStatus() {
            assertAll("availability by status",
                () -> {
                    worker.setStatus(WorkerStatus.IDLE);
                    assertTrue(worker.isAvailable(), "IDLE worker should be available");
                },
                () -> {
                    worker.setStatus(WorkerStatus.BUSY);
                    assertTrue(worker.isAvailable(), "BUSY worker should be available");
                },
                () -> {
                    worker.setStatus(WorkerStatus.OVERLOADED);
                    assertFalse(worker.isAvailable(), "OVERLOADED worker should not be available");
                },
                () -> {
                    worker.setStatus(WorkerStatus.UNRESPONSIVE);
                    assertFalse(worker.isAvailable(), "UNRESPONSIVE worker should not be available");
                },
                () -> {
                    worker.setStatus(WorkerStatus.DEAD);
                    assertFalse(worker.isAvailable(), "DEAD worker should not be available");
                }
            );
        }
    }

    @Nested
    @DisplayName("Heartbeat and load")
    class HeartbeatAndLoad {

        private WorkerInfo worker;

        @BeforeEach
        void setUp() {
            worker = new WorkerInfo(WORKER_ID, HOSTNAME, PORT, CORES);
        }

        @Test
        @DisplayName("updateLastHeartbeat updates timestamp")
        void test_WhenUpdateHeartbeat_ThenTimestampUpdated() {
            long before = System.currentTimeMillis();
            worker.updateLastHeartbeat();
            long after = System.currentTimeMillis();

            long heartbeat = worker.getLastHeartbeatMs();
            assertTrue(heartbeat >= before && heartbeat <= after,
                    "heartbeat timestamp should be between before and after");
        }

        @Test
        @DisplayName("incrementLoad increases load by 1")
        void test_WhenIncrementLoad_ThenLoadIncreasedByOne() {
            int initial = worker.getCurrentLoad();
            int result = worker.incrementLoad();

            assertAll("increment",
                () -> assertEquals(initial + 1, result),
                () -> assertEquals(initial + 1, worker.getCurrentLoad())
            );
        }

        @Test
        @DisplayName("decrementLoad decreases load by 1")
        void test_WhenDecrementLoad_ThenLoadDecreasedByOne() {
            worker.incrementLoad();
            worker.incrementLoad();
            int before = worker.getCurrentLoad();
            int result = worker.decrementLoad();

            assertAll("decrement",
                () -> assertEquals(before - 1, result),
                () -> assertEquals(before - 1, worker.getCurrentLoad())
            );
        }

        @Test
        @DisplayName("decrementLoad at zero stays at zero")
        void test_WhenDecrementAtZero_ThenRemainsZero() {
            int result = worker.decrementLoad();

            assertAll("no negative load",
                () -> assertEquals(0, result),
                () -> assertEquals(0, worker.getCurrentLoad())
            );
        }

        @Test
        @DisplayName("getLoadFactor returns correct ratio")
        void test_WhenLoadApplied_ThenLoadFactorCorrect() {
            worker.incrementLoad();
            worker.incrementLoad();
            // 2 tasks / 4 cores = 0.5
            assertEquals(0.5, worker.getLoadFactor(), 1e-9,
                    "loadFactor should be currentLoad / availableCores");
        }
    }

    @Nested
    @DisplayName("Equality and hashing")
    class EqualityAndHashing {

        @Test
        @DisplayName("equals and hashCode based on workerId — same ID means equal")
        void test_WhenSameWorkerId_ThenEqualAndSameHash() {
            WorkerInfo a = new WorkerInfo(WORKER_ID, "host-a", 1111, 2);
            WorkerInfo b = new WorkerInfo(WORKER_ID, "host-b", 2222, 8);

            assertAll("equals/hashCode by workerId",
                () -> assertEquals(a, b, "same workerId should be equal"),
                () -> assertEquals(a.hashCode(), b.hashCode(),
                        "same workerId should yield same hashCode")
            );
        }

        @Test
        @DisplayName("Different workerIds are not equal")
        void test_WhenDifferentWorkerId_ThenNotEqual() {
            WorkerInfo a = new WorkerInfo("worker-A", HOSTNAME, PORT, CORES);
            WorkerInfo b = new WorkerInfo("worker-B", HOSTNAME, PORT, CORES);

            assertNotEquals(a, b, "different workerIds should not be equal");
        }
    }
}
