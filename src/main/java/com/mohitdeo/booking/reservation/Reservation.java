package com.mohitdeo.booking.reservation;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.mohitdeo.booking.api.Errors.InvalidReservationStateException;

@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    /** SHA-256 of the original request, used to spot an Idempotency-Key reused with a different body. */
    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "reservation_seats", joinColumns = @JoinColumn(name = "reservation_id"))
    @Column(name = "seat_id", nullable = false)
    private Set<UUID> seatIds = new HashSet<>();

    @Version
    private Long version;

    protected Reservation() {
    }

    static Reservation hold(UUID id, UUID eventId, String userId, Collection<UUID> seatIds,
                            String idempotencyKey, String requestHash, Instant now, Duration ttl) {
        Reservation r = new Reservation();
        r.id = id;
        r.eventId = eventId;
        r.userId = userId;
        r.seatIds = new HashSet<>(seatIds);
        r.idempotencyKey = idempotencyKey;
        r.requestHash = requestHash;
        r.status = ReservationStatus.HELD;
        r.createdAt = now;
        r.updatedAt = now;
        r.expiresAt = now.plus(ttl);
        return r;
    }

    public boolean isHoldExpiredAt(Instant now) {
        return status == ReservationStatus.HELD && !now.isBefore(expiresAt);
    }

    void transitionTo(ReservationStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidReservationStateException(id, status, target);
        }
        this.status = target;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getUserId() {
        return userId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Set<UUID> getSeatIds() {
        return Set.copyOf(seatIds);
    }
}
