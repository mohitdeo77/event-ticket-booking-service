package com.mohitdeo.booking.reservation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    Optional<Reservation> findByIdempotencyKey(String idempotencyKey);

    /** Confirm, cancel and expiry all lock the reservation first, so they can never run on it at the same time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> findByIdForUpdate(UUID id);

    @Query("""
            select r.id from Reservation r
            where r.status = com.mohitdeo.booking.reservation.ReservationStatus.HELD
              and r.expiresAt <= :now
            order by r.expiresAt
            """)
    List<UUID> findExpiredHoldIds(Instant now, Pageable page);
}
