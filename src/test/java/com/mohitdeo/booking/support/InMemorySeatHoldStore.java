package com.mohitdeo.booking.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mohitdeo.booking.hold.SeatHoldStore;

/**
 * Behaves like the Redis Lua scripts (all-or-nothing hold, owner-checked release, TTL),
 * so tests need no Redis server. Setting {@code bypass} turns the gate off, so tests reach
 * the database locks directly, as happens when Redis is down.
 */
public class InMemorySeatHoldStore implements SeatHoldStore {

    private record Hold(UUID reservationId, Instant expiresAt) {
    }

    private final Map<String, Hold> holds = new HashMap<>();
    private final Clock clock;
    private volatile boolean bypass;

    public InMemorySeatHoldStore(Clock clock) {
        this.clock = clock;
    }

    public void setBypass(boolean bypass) {
        this.bypass = bypass;
    }

    @Override
    public synchronized boolean tryHoldAll(UUID eventId, Collection<UUID> seatIds, UUID reservationId, Duration ttl) {
        if (bypass) {
            return true;
        }
        Instant now = clock.instant();
        boolean anyHeld = seatIds.stream()
                .map(id -> holds.get(key(eventId, id)))
                .anyMatch(h -> h != null && now.isBefore(h.expiresAt()));
        if (anyHeld) {
            return false;
        }
        seatIds.forEach(id -> holds.put(key(eventId, id), new Hold(reservationId, now.plus(ttl))));
        return true;
    }

    @Override
    public synchronized void releaseAll(UUID eventId, Collection<UUID> seatIds, UUID reservationId) {
        seatIds.forEach(id -> holds.computeIfPresent(key(eventId, id),
                (k, h) -> h.reservationId().equals(reservationId) ? null : h));
    }

    public synchronized boolean isHeld(UUID eventId, UUID seatId) {
        Hold h = holds.get(key(eventId, seatId));
        return h != null && clock.instant().isBefore(h.expiresAt());
    }

    private static String key(UUID eventId, UUID seatId) {
        return eventId + ":" + seatId;
    }
}
