package com.possaas.common.id;

import com.github.f4b6a3.uuid.UuidCreator;
import java.util.UUID;

/**
 * UUIDv7 generation, matching the {@code uuid_generate_v7()} default in the schema.
 *
 * <p>Two reasons this is not {@code UUID.randomUUID()}:
 * <ul>
 *   <li>v7 embeds a millisecond timestamp, so inserts append to the right-hand edge of
 *       the primary-key index instead of scattering across it.</li>
 *   <li>A POS terminal can mint an id before the server has seen the sale, which is the
 *       prerequisite for the offline queue we plan to add without a schema change.</li>
 * </ul>
 */
public final class Uuid {

    private Uuid() {
    }

    public static UUID v7() {
        return UuidCreator.getTimeOrderedEpoch();
    }

    /** Parses a UUID, returning null for null or blank input rather than throwing. */
    public static UUID parseOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
