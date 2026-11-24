package com.mohitdeo.booking.seat;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

public interface SeatRepository extends JpaRepository<Seat, UUID> {

    /**
     * Locks the requested seats with SELECT ... FOR UPDATE.
     * <p>
     * Rows are always locked in primary-key order. Two requests for overlapping seat sets
     * (for example {A1, A2} and {A2, A3}) therefore take locks in the same order and cannot
     * deadlock each other. The lock timeout makes a blocked request fail fast instead of
     * waiting forever.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select s from Seat s where s.eventId = :eventId and s.id in :seatIds order by s.id")
    List<Seat> findAllForUpdate(UUID eventId, Collection<UUID> seatIds);

    List<Seat> findByEventIdOrderByRowLabelAscSeatNumberAsc(UUID eventId);
}
