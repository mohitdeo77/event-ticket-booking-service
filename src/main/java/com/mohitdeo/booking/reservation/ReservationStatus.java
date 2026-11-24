package com.mohitdeo.booking.reservation;

/**
 * Reservation lifecycle:
 * <pre>
 *            ┌──► CONFIRMED
 *   HELD ────┼──► CANCELLED
 *            └──► EXPIRED
 * </pre>
 * HELD is the only state that can change. Every other state is final.
 */
public enum ReservationStatus {
    HELD,
    CONFIRMED,
    CANCELLED,
    EXPIRED;

    public boolean canTransitionTo(ReservationStatus target) {
        return this == HELD && target != HELD;
    }
}
