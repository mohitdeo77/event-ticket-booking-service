package com.mohitdeo.booking.hold;

import java.time.Duration;
import java.util.Collection;
import java.util.UUID;

/**
 * Short-lived seat holds with a TTL, kept outside the database.
 * <p>
 * This is a fast first gate: when many users go for the same seat, all but one are turned
 * away here without touching PostgreSQL. It is <b>not</b> the source of truth. The database
 * row lock and seat status decide the outcome, so a lost or expired hold can never cause a
 * double booking.
 */
public interface SeatHoldStore {

    /** Holds all seats for the reservation, or none of them if any seat is already held. */
    boolean tryHoldAll(UUID eventId, Collection<UUID> seatIds, UUID reservationId, Duration ttl);

    /** Removes holds that still belong to this reservation. Holds now owned by others are left alone. */
    void releaseAll(UUID eventId, Collection<UUID> seatIds, UUID reservationId);
}
