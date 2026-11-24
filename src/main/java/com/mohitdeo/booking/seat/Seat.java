package com.mohitdeo.booking.seat;

import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A single seat for one event. Seat state changes only through the methods below, and only
 * while the row is locked (SELECT ... FOR UPDATE). The {@code @Version} column is a second
 * guard: if two transactions ever see the same version, the second commit fails instead of
 * silently double-booking.
 */
@Entity
@Table(name = "seats")
public class Seat {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "row_label", nullable = false)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false)
    private int seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatStatus status;

    @Column(name = "reservation_id")
    private UUID reservationId;

    @Version
    private Long version;

    protected Seat() {
    }

    public Seat(UUID eventId, String rowLabel, int seatNumber) {
        this.id = UUID.randomUUID();
        this.eventId = eventId;
        this.rowLabel = rowLabel;
        this.seatNumber = seatNumber;
        this.status = SeatStatus.AVAILABLE;
    }

    public boolean isAvailable() {
        return status == SeatStatus.AVAILABLE;
    }

    public void hold(UUID reservationId) {
        if (!isAvailable()) {
            throw new IllegalStateException("Seat " + id + " is " + status + ", cannot hold");
        }
        this.status = SeatStatus.HELD;
        this.reservationId = reservationId;
    }

    public void book(UUID reservationId) {
        requireHeldBy(reservationId);
        this.status = SeatStatus.BOOKED;
    }

    /** Releases the seat only if it is still held by this reservation. Returns true if released. */
    public boolean release(UUID reservationId) {
        if (status != SeatStatus.HELD || !Objects.equals(this.reservationId, reservationId)) {
            return false;
        }
        this.status = SeatStatus.AVAILABLE;
        this.reservationId = null;
        return true;
    }

    private void requireHeldBy(UUID reservationId) {
        if (status != SeatStatus.HELD || !Objects.equals(this.reservationId, reservationId)) {
            throw new IllegalStateException("Seat " + id + " is not held by reservation " + reservationId);
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getRowLabel() {
        return rowLabel;
    }

    public int getSeatNumber() {
        return seatNumber;
    }

    public SeatStatus getStatus() {
        return status;
    }

    public UUID getReservationId() {
        return reservationId;
    }
}
