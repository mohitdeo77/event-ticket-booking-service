package com.mohitdeo.booking.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.mohitdeo.booking.api.Errors.IdempotencyKeyReusedException;
import com.mohitdeo.booking.api.Errors.InvalidReservationStateException;
import com.mohitdeo.booking.api.Errors.ReservationExpiredException;
import com.mohitdeo.booking.api.Errors.SeatsUnavailableException;
import com.mohitdeo.booking.event.EventDtos.CreateEventRequest;
import com.mohitdeo.booking.event.EventDtos.SeatView;
import com.mohitdeo.booking.event.EventService;
import com.mohitdeo.booking.reservation.ReservationDtos.HoldResult;
import com.mohitdeo.booking.reservation.ReservationDtos.HoldSeatsRequest;
import com.mohitdeo.booking.reservation.ReservationDtos.ReservationView;
import com.mohitdeo.booking.seat.SeatStatus;
import com.mohitdeo.booking.support.InMemorySeatHoldStore;
import com.mohitdeo.booking.support.MutableClock;
import com.mohitdeo.booking.support.TestInfrastructure;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestInfrastructure.class)
class ReservationServiceTest {

    @Autowired
    ReservationService reservationService;
    @Autowired
    EventService eventService;
    @Autowired
    MutableClock clock;
    @Autowired
    InMemorySeatHoldStore holdStore;

    UUID eventId;
    List<SeatView> seats;

    @BeforeEach
    void createEvent() {
        holdStore.setBypass(false);
        eventId = eventService.create(new CreateEventRequest("Concert", "Arena",
                Instant.parse("2026-06-01T19:00:00Z"), 2, 5)).id();
        seats = eventService.seatMap(eventId);
    }

    @AfterEach
    void resetBypass() {
        holdStore.setBypass(false);
    }

    @Test
    void holdThenConfirmBooksTheSeats() {
        HoldResult hold = hold("user-1", key(), seat(0), seat(1));

        assertThat(hold.created()).isTrue();
        assertThat(hold.reservation().status()).isEqualTo(ReservationStatus.HELD);
        assertThat(statusOf(seat(0))).isEqualTo(SeatStatus.HELD);

        ReservationView confirmed = reservationService.confirm(hold.reservation().id());

        assertThat(confirmed.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(statusOf(seat(0))).isEqualTo(SeatStatus.BOOKED);
        assertThat(statusOf(seat(1))).isEqualTo(SeatStatus.BOOKED);
    }

    @Test
    void confirmIsIdempotent() {
        UUID id = hold("user-1", key(), seat(0)).reservation().id();

        reservationService.confirm(id);
        ReservationView again = reservationService.confirm(id);

        assertThat(again.status()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void heldSeatCannotBeHeldByAnotherUser() {
        hold("user-1", key(), seat(0));

        assertThatThrownBy(() -> hold("user-2", key(), seat(0)))
                .isInstanceOf(SeatsUnavailableException.class);
    }

    @Test
    void bookedSeatIsRejectedByDatabaseEvenWhenRedisGateIsDown() {
        UUID id = hold("user-1", key(), seat(0)).reservation().id();
        reservationService.confirm(id);

        holdStore.setBypass(true); // simulate Redis outage: only the DB stands in the way

        assertThatThrownBy(() -> hold("user-2", key(), seat(0)))
                .isInstanceOf(SeatsUnavailableException.class);
    }

    @Test
    void sameIdempotencyKeyReplaysOriginalReservation() {
        String key = key();
        HoldResult first = hold("user-1", key, seat(0), seat(1));
        // Same seats in a different order: still the same logical request
        HoldResult replay = hold("user-1", key, seat(1), seat(0));

        assertThat(replay.created()).isFalse();
        assertThat(replay.reservation().id()).isEqualTo(first.reservation().id());
    }

    @Test
    void idempotencyKeyReusedWithDifferentBodyIsRejected() {
        String key = key();
        hold("user-1", key, seat(0));

        assertThatThrownBy(() -> hold("user-1", key, seat(2)))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    void cancelReleasesSeatsForOthers() {
        UUID id = hold("user-1", key(), seat(0)).reservation().id();

        ReservationView cancelled = reservationService.cancel(id);

        assertThat(cancelled.status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(statusOf(seat(0))).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(holdStore.isHeld(eventId, seat(0))).isFalse();
        assertThat(hold("user-2", key(), seat(0)).created()).isTrue();
    }

    @Test
    void confirmedReservationCannotBeCancelled() {
        UUID id = hold("user-1", key(), seat(0)).reservation().id();
        reservationService.confirm(id);

        assertThatThrownBy(() -> reservationService.cancel(id))
                .isInstanceOf(InvalidReservationStateException.class);
    }

    @Test
    void sweeperExpiresStaleHoldsAndReleasesSeats() {
        UUID id = hold("user-1", key(), seat(0), seat(1)).reservation().id();

        clock.advance(Duration.ofMinutes(5).plusSeconds(1));
        int expired = reservationService.expireDueHolds();

        assertThat(expired).isGreaterThanOrEqualTo(1);
        assertThat(reservationService.get(id).status()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(statusOf(seat(0))).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(statusOf(seat(1))).isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    void confirmAfterHoldExpiredFailsAndReleasesSeats() {
        UUID id = hold("user-1", key(), seat(0)).reservation().id();

        clock.advance(Duration.ofMinutes(6));

        assertThatThrownBy(() -> reservationService.confirm(id))
                .isInstanceOf(ReservationExpiredException.class);
        assertThat(reservationService.get(id).status()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(statusOf(seat(0))).isEqualTo(SeatStatus.AVAILABLE);
    }

    // ---------------------------------------------------------------- helpers

    private HoldResult hold(String userId, String key, UUID... seatIds) {
        return reservationService.hold(eventId, new HoldSeatsRequest(userId, Set.of(seatIds)), key);
    }

    private UUID seat(int index) {
        return seats.get(index).id();
    }

    private SeatStatus statusOf(UUID seatId) {
        return eventService.seatMap(eventId).stream()
                .filter(s -> s.id().equals(seatId))
                .findFirst().orElseThrow()
                .status();
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }
}
