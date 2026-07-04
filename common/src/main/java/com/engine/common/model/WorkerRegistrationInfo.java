package com.engine.common.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * Immutable data carrier sent by a worker node during the registration handshake.
 *
 * <p>An instance of this record is serialized and embedded as the payload of a
 * {@code REGISTER_REQUEST} message. The master uses the information to catalogue
 * the worker, assign it a canonical identifier (if {@code workerId} is
 * {@code null}), and determine initial scheduling capacity based on
 * {@code availableCores}.
 *
 * <p><b>Invariants enforced by the compact constructor:</b>
 * <ul>
 *   <li>{@code hostname} must not be {@code null} or blank.</li>
 *   <li>{@code availableCores} must be greater than zero.</li>
 * </ul>
 *
 * @param workerId       the worker's self-assigned identifier, or {@code null}
 *                       if the master should assign one
 * @param hostname       the worker's hostname (must not be {@code null} or blank)
 * @param port           the worker's listening port reserved for future
 *                       bidirectional communication
 * @param availableCores the number of CPU cores the worker can dedicate to
 *                       task execution (must be &gt; 0)
 *
 * @author Engine Team
 */
public record WorkerRegistrationInfo(
        String workerId,
        String hostname,
        int port,
        int availableCores
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Compact constructor that validates registration fields.
     *
     * @throws IllegalArgumentException if {@code hostname} is {@code null} or
     *                                  blank, or if {@code availableCores} is
     *                                  not positive
     */
    public WorkerRegistrationInfo {
        if (hostname == null || hostname.isBlank()) {
            throw new IllegalArgumentException("hostname must not be null or blank");
        }
        if (availableCores <= 0) {
            throw new IllegalArgumentException("availableCores must be greater than 0");
        }
    }
}
