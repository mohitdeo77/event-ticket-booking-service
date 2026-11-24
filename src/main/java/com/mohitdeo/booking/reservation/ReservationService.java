package com.mohitdeo.booking.reservation;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.mohitdeo.booking.api.Errors.IdempotencyKeyReusedException;
import com.mohitdeo.booking.api.Errors.InvalidRequestException;
import com.mohitdeo.booking.api.Errors.NotFoundException;
import com.mohitdeo.booking.api.Errors.RequestInProgressException;
import com.mohitdeo.booking.api.Errors.ReservationExpiredException;
import com.mohitdeo.booking.api.Errors.SeatsUnavailableException;
import com.mohitdeo.booking.config.ReservationProperties;
import com.mohitdeo.booking.event.EventRepository;
import com.mohitdeo.booking.hold.IdempotencyLock;
import com.mohitdeo.booking.hold.SeatHoldStore;
import com.mohitdeo.booking.reservation.ReservationDtos.HoldResult;
import com.mohitdeo.booking.reservation.ReservationDtos.HoldSeatsRequest;
import com.mohitdeo.booking.reservation.ReservationDtos.ReservationView;
import com.mohitdeo.booking.seat.Seat;
import com.mohitdeo.booking.seat.SeatRepository;

/**
 * Seat reservation workflow: hold, then confirm or cancel. Holds expire on their own.
 *
 * <h2>How double booking is prevented</h2>
 * <ol>
 *   <li><b>Redis gate</b>: an atomic, TTL-based hold on every requested seat. Most competing
 *       requests are rejected here, before they open a database transaction.</li>
 *   <li><b>Row locks</b>: inside a READ COMMITTED transaction the seats are locked with
 *       SELECT ... FOR UPDATE, in a fixed order, and their status is checked again.
 *       This is the source of truth.</li>
 *   <li><b>Optimistic version</b>: {@code @Version} on Seat makes any lost update fail
 *       at commit time instead of being applied.</li>
 * </ol>
 * Redis calls are made only outside database transactions, so a slow Redis never holds a row lock.
 */
@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final ReservationRepository reservations;
    private final SeatRepository seats;
    private final EventRepository events;
    private final SeatHoldStore holdStore;
    private final IdempotencyLock idempotencyLock;
    private final ReservationProperties props;
    private final Clock clock;
    private final TransactionTemplate tx;

    public ReservationService(ReservationRepository reservations, SeatRepository seats, EventRepository events,
                              SeatHoldStore holdStore, IdempotencyLock idempotencyLock,
                              ReservationProperties props, Clock clock, PlatformTransactionManager txManager) {
        this.reservations = reservations;
        this.seats = seats;
        this.events = events;
        this.holdStore = holdStore;
        this.idempotencyLock = idempotencyLock;
        this.props = props;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
        this.tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    // ---------------------------------------------------------------- hold

    public HoldResult hold(UUID eventId, HoldSeatsRequest request, String idempotencyKey) {
        Set<UUID> seatIds = new TreeSet<>(request.seatIds());
        if (seatIds.size() > props.maxSeatsPerReservation()) {
            throw new InvalidRequestException("At most " + props.maxSeatsPerReservation() + " seats per reservation");
        }
        String requestHash = RequestFingerprint.of(eventId, request.userId(), seatIds);

        Optional<HoldResult> replay = findReplay(idempotencyKey, requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }

        if (!idempotencyLock.tryLock(idempotencyKey, props.idempotencyLockTtl())) {
            // A request with this key is running now. If it has already finished, return its result.
            return findReplay(idempotencyKey, requestHash)
                    .orElseThrow(() -> new RequestInProgressException(idempotencyKey));
        }
        try {
            // Check again now that we hold the lock: the previous owner may have just committed
            replay = findReplay(idempotencyKey, requestHash);
            if (replay.isPresent()) {
                return replay.get();
            }
            return placeHold(eventId, request.userId(), seatIds, idempotencyKey, requestHash);
        } finally {
            idempotencyLock.unlock(idempotencyKey);
        }
    }

    private HoldResult placeHold(UUID eventId, String userId, Set<UUID> seatIds,
                                 String idempotencyKey, String requestHash) {
        UUID reservationId = UUID.randomUUID();

        if (!holdStore.tryHoldAll(eventId, seatIds, reservationId, props.holdTtl())) {
            throw new SeatsUnavailableException(seatIds);
        }
        try {
            Reservation saved = tx.execute(status -> {
                if (!events.existsById(eventId)) {
                    throw new NotFoundException("Event", eventId);
                }
                List<Seat> locked = seats.findAllForUpdate(eventId, seatIds);
                if (locked.size() != seatIds.size()) {
                    throw new InvalidRequestException("One or more seats do not belong to event " + eventId);
                }
                List<UUID> unavailable = locked.stream().filter(s -> !s.isAvailable()).map(Seat::getId).toList();
                if (!unavailable.isEmpty()) {
                    throw new SeatsUnavailableException(unavailable);
                }
                Reservation reservation = Reservation.hold(reservationId, eventId, userId, seatIds,
                        idempotencyKey, requestHash, clock.instant(), props.holdTtl());
                locked.forEach(seat -> seat.hold(reservationId));
                return reservations.saveAndFlush(reservation);
            });
            log.info("Reservation {} holds {} seat(s) for event {}", reservationId, seatIds.size(), eventId);
            return new HoldResult(ReservationView.from(saved), true);
        } catch (DataIntegrityViolationException e) {
            // Last line of defence: unique(idempotency_key) caught a duplicate the Redis lock missed
            holdStore.releaseAll(eventId, seatIds, reservationId);
            return findReplay(idempotencyKey, requestHash).orElseThrow(() -> e);
        } catch (OptimisticLockingFailureException | PessimisticLockingFailureException e) {
            // Another transaction changed or is still locking these seats
            holdStore.releaseAll(eventId, seatIds, reservationId);
            throw new SeatsUnavailableException(seatIds);
        } catch (RuntimeException e) {
            // Undo the Redis hold so these seats do not stay blocked until the TTL runs out
            holdStore.releaseAll(eventId, seatIds, reservationId);
            throw e;
        }
    }

    private Optional<HoldResult> findReplay(String idempotencyKey, String requestHash) {
        return reservations.findByIdempotencyKey(idempotencyKey).map(existing -> {
            if (!existing.getRequestHash().equals(requestHash)) {
                throw new IdempotencyKeyReusedException(idempotencyKey);
            }
            return new HoldResult(ReservationView.from(existing), false);
        });
    }

    // ---------------------------------------------------------------- confirm / cancel

    /** Turns a hold into a booking. Confirming an already confirmed reservation returns it unchanged. */
    public ReservationView confirm(UUID reservationId) {
        Reservation result = tx.execute(status -> {
            Reservation r = lockReservation(reservationId);
            if (r.getStatus() == ReservationStatus.CONFIRMED) {
                return r;
            }
            Instant now = clock.instant();
            if (r.isHoldExpiredAt(now)) {
                expire(r, now);
                return r;
            }
            r.transitionTo(ReservationStatus.CONFIRMED, now);
            seats.findAllForUpdate(r.getEventId(), r.getSeatIds()).forEach(seat -> seat.book(reservationId));
            return r;
        });

        if (result.getStatus() == ReservationStatus.EXPIRED) {
            holdStore.releaseAll(result.getEventId(), result.getSeatIds(), reservationId);
            throw new ReservationExpiredException(reservationId);
        }
        return ReservationView.from(result);
    }

    /** Cancels a hold and puts its seats back on sale. Cancelling twice has no further effect. */
    public ReservationView cancel(UUID reservationId) {
        Reservation result = tx.execute(status -> {
            Reservation r = lockReservation(reservationId);
            if (r.getStatus() == ReservationStatus.CANCELLED) {
                return r;
            }
            Instant now = clock.instant();
            r.transitionTo(ReservationStatus.CANCELLED, now);
            releaseSeats(r);
            return r;
        });
        holdStore.releaseAll(result.getEventId(), result.getSeatIds(), reservationId);
        return ReservationView.from(result);
    }

    public ReservationView get(UUID reservationId) {
        return reservations.findById(reservationId)
                .map(ReservationView::from)
                .orElseThrow(() -> new NotFoundException("Reservation", reservationId));
    }

    // ---------------------------------------------------------------- expiry

    /**
     * Releases holds whose TTL has passed. Each reservation is expired in its own short
     * transaction, so one failure does not undo the rest of the batch. The status is checked
     * again under the row lock, which makes this safe to run on several instances at once.
     *
     * @return number of reservations expired
     */
    public int expireDueHolds() {
        Instant now = clock.instant();
        List<UUID> due = reservations.findExpiredHoldIds(now, PageRequest.of(0, props.sweepBatchSize()));
        int expired = 0;
        for (UUID id : due) {
            try {
                Optional<Reservation> result = tx.execute(status -> {
                    Reservation r = lockReservation(id);
                    if (!r.isHoldExpiredAt(now)) {
                        return Optional.<Reservation>empty();
                    }
                    expire(r, now);
                    return Optional.of(r);
                });
                if (result != null && result.isPresent()) {
                    Reservation r = result.get();
                    holdStore.releaseAll(r.getEventId(), r.getSeatIds(), r.getId());
                    expired++;
                }
            } catch (RuntimeException e) {
                log.warn("Could not expire reservation {}; will retry on next sweep", id, e);
            }
        }
        if (expired > 0) {
            log.info("Expired {} stale hold(s)", expired);
        }
        return expired;
    }

    private void expire(Reservation r, Instant now) {
        r.transitionTo(ReservationStatus.EXPIRED, now);
        releaseSeats(r);
    }

    private void releaseSeats(Reservation r) {
        seats.findAllForUpdate(r.getEventId(), r.getSeatIds()).forEach(seat -> seat.release(r.getId()));
    }

    private Reservation lockReservation(UUID id) {
        return reservations.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Reservation", id));
    }
}
