package com.engine.master.registry;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("WorkerRegistry — worker registration and lookup")
class WorkerRegistryTest {

    private WorkerRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new WorkerRegistry();
    }

    private WorkerInfo createWorker(String id) {
        return new WorkerInfo(id, "host-" + id, 8080, 4);
    }

    @Nested
    @DisplayName("Registration")
    class Registration {

        @Test
        @DisplayName("Register worker and retrieve by ID")
        void test_WhenWorkerRegistered_ThenRetrievableById() {
            WorkerInfo worker = createWorker("w1");
            registry.registerWorker(worker);

            Optional<WorkerInfo> found = registry.getWorker("w1");

            assertAll("registration",
                () -> assertTrue(found.isPresent(), "worker should be found"),
                () -> assertEquals("w1", found.get().getWorkerId())
            );
        }

        @Test
        @DisplayName("Register worker increases count")
        void test_WhenWorkerRegistered_ThenCountIncreases() {
            assertEquals(0, registry.getWorkerCount(), "empty registry");

            registry.registerWorker(createWorker("w1"));
            assertEquals(1, registry.getWorkerCount());

            registry.registerWorker(createWorker("w2"));
            assertEquals(2, registry.getWorkerCount());
        }

        @Test
        @DisplayName("Register duplicate worker replaces existing")
        void test_WhenDuplicateRegistered_ThenExistingReplaced() {
            WorkerInfo original = new WorkerInfo("w1", "host-old", 1111, 2);
            WorkerInfo replacement = new WorkerInfo("w1", "host-new", 2222, 8);

            registry.registerWorker(original);
            registry.registerWorker(replacement);

            assertAll("replacement",
                () -> assertEquals(1, registry.getWorkerCount(),
                        "count should remain 1"),
                () -> assertEquals("host-new",
                        registry.getWorker("w1").get().getHostname(),
                        "hostname should be from the replacement")
            );
        }
    }

    @Nested
    @DisplayName("Deregistration")
    class Deregistration {

        @Test
        @DisplayName("Deregister removes worker")
        void test_WhenDeregistered_ThenWorkerRemoved() {
            registry.registerWorker(createWorker("w1"));
            boolean removed = registry.deregisterWorker("w1");

            assertAll("deregister",
                () -> assertTrue(removed, "should return true"),
                () -> assertFalse(registry.hasWorker("w1")),
                () -> assertEquals(0, registry.getWorkerCount())
            );
        }

        @Test
        @DisplayName("Deregister non-existent worker returns false")
        void test_WhenDeregisterNonExistent_ThenReturnsFalse() {
            assertFalse(registry.deregisterWorker("ghost"),
                    "deregistering unknown worker should return false");
        }
    }

    @Nested
    @DisplayName("Lookup")
    class Lookup {

        @Test
        @DisplayName("getWorker returns empty Optional for unknown ID")
        void test_WhenUnknownId_ThenEmptyOptional() {
            assertTrue(registry.getWorker("nope").isEmpty(),
                    "unknown ID should yield empty Optional");
        }

        @Test
        @DisplayName("hasWorker returns true for registered, false for unknown")
        void test_WhenChecked_ThenHasWorkerReflectsState() {
            registry.registerWorker(createWorker("w1"));

            assertAll("hasWorker",
                () -> assertTrue(registry.hasWorker("w1")),
                () -> assertFalse(registry.hasWorker("w2"))
            );
        }
    }

    @Nested
    @DisplayName("Status and heartbeat updates")
    class Updates {

        @Test
        @DisplayName("updateStatus changes worker status")
        void test_WhenUpdateStatus_ThenStatusChanges() {
            registry.registerWorker(createWorker("w1"));
            registry.updateStatus("w1", WorkerStatus.BUSY);

            assertEquals(WorkerStatus.BUSY,
                    registry.getWorker("w1").get().getStatus());
        }

        @Test
        @DisplayName("updateHeartbeat updates worker timestamp")
        void test_WhenUpdateHeartbeat_ThenTimestampUpdated() {
            registry.registerWorker(createWorker("w1"));
            long before = System.currentTimeMillis();

            registry.updateHeartbeat("w1");

            long heartbeat = registry.getWorker("w1").get().getLastHeartbeatMs();
            assertTrue(heartbeat >= before,
                    "heartbeat should be at or after the call time");
        }
    }

    @Nested
    @DisplayName("Filtering workers")
    class Filtering {

        @BeforeEach
        void populate() {
            WorkerInfo idle = createWorker("idle-1");

            WorkerInfo busy = createWorker("busy-1");
            busy.setStatus(WorkerStatus.BUSY);

            WorkerInfo overloaded = createWorker("over-1");
            overloaded.setStatus(WorkerStatus.OVERLOADED);

            WorkerInfo unresponsive = createWorker("unresp-1");
            unresponsive.setStatus(WorkerStatus.UNRESPONSIVE);

            WorkerInfo dead = createWorker("dead-1");
            dead.setStatus(WorkerStatus.DEAD);

            registry.registerWorker(idle);
            registry.registerWorker(busy);
            registry.registerWorker(overloaded);
            registry.registerWorker(unresponsive);
            registry.registerWorker(dead);
        }

        @Test
        @DisplayName("getAvailableWorkers returns only IDLE and BUSY")
        void test_WhenMixedStatuses_ThenOnlyAvailableReturned() {
            List<WorkerInfo> available = registry.getAvailableWorkers();

            assertAll("available workers",
                () -> assertEquals(2, available.size(),
                        "only IDLE and BUSY are available"),
                () -> assertTrue(
                        available.stream().allMatch(WorkerInfo::isAvailable),
                        "all returned workers must be available")
            );
        }

        @Test
        @DisplayName("getAvailableWorkers excludes UNRESPONSIVE and DEAD")
        void test_WhenMixedStatuses_ThenUnresponsiveAndDeadExcluded() {
            List<WorkerInfo> available = registry.getAvailableWorkers();

            assertAll("exclusions",
                () -> assertTrue(available.stream()
                        .noneMatch(w -> w.getStatus() == WorkerStatus.UNRESPONSIVE),
                        "UNRESPONSIVE must be excluded"),
                () -> assertTrue(available.stream()
                        .noneMatch(w -> w.getStatus() == WorkerStatus.DEAD),
                        "DEAD must be excluded")
            );
        }

        @Test
        @DisplayName("getAllWorkers returns all regardless of status")
        void testGetAllWorkers() {
            registry.registerWorker(new WorkerInfo("w1", "host-w1", 9091, 4));
            registry.registerWorker(new WorkerInfo("w2", "host-w2", 9092, 4));

            Collection<WorkerInfo> allWorkers = registry.getAllWorkers();
            assertEquals(7, allWorkers.size());
        }
    }
}
