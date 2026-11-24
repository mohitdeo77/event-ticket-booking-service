package com.mohitdeo.booking.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.mohitdeo.booking.api.Errors.SeatsUnavailableException;
import com.mohitdeo.booking.event.EventDtos.CreateEventRequest;
import com.mohitdeo.booking.event.EventDtos.SeatView;
import com.mohitdeo.booking.event.EventService;
import com.mohitdeo.booking.reservation.ReservationDtos.HoldSeatsRequest;
import com.mohitdeo.booking.seat.SeatStatus;
import com.mohitdeo.booking.support.InMemorySeatHoldStore;
import com.mohitdeo.booking.support.TestInfrastructure;

/**
 * Many threads race for the same seats at the same moment. Each scenario runs twice:
 * once with the Redis-style gate, and once with the gate off, so the database row locks
 * alone must prevent double booking.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestInfrastructure.class)
class ConcurrentBookingTest {

    private static final int THREADS = 24;

    @Autowired
    ReservationService reservationService;
    @Autowired
    EventService eventService;
    @Autowired
    InMemorySeatHoldStore holdStore;

    UUID eventId;
    List<SeatView> seats;
    ExecutorService pool;

    @BeforeEach
    void setUp() {
        eventId = eventService.create(new CreateEventRequest("Final", "Stadium",
                Instant.parse("2026-07-01T19:00:00Z"), 1, 10)).id();
        seats = eventService.seatMap(eventId);
        pool = Executors.newFixedThreadPool(THREADS);
    }

    @AfterEach
    void tearDown() {
        holdStore.setBypass(false);
        pool.shutdownNow();
    }

    @ParameterizedTest(name = "redis gate bypassed = {0}")
    @ValueSource(booleans = {false, true})
    void exactlyOneRequestWinsTheSameSeat(boolean bypassGate) throws Exception {
        holdStore.setBypass(bypassGate);
        UUID contested = seats.get(0).id();

        Outcome outcome = race(i -> Set.of(contested));

        assertThat(outcome.succeeded()).isEqualTo(1);
        assertThat(outcome.rejected()).isEqualTo(THREADS - 1);
        assertThat(outcome.unexpected()).isZero();
        assertThat(seatStatus(contested)).isEqualTo(SeatStatus.HELD);
    }

    @ParameterizedTest(name = "redis gate bypassed = {0}")
    @ValueSource(booleans = {false, true})
    void overlappingSeatSetsNeverShareASeat(boolean bypassGate) throws Exception {
        holdStore.setBypass(bypassGate);
        // Thread i asks for seats {i % 9, i % 9 + 1}: neighbouring requests overlap by one seat
        Outcome outcome = race(i -> Set.of(seats.get(i % 9).id(), seats.get(i % 9 + 1).id()));

        assertThat(outcome.unexpected()).isZero();
        assertThat(outcome.succeeded()).isPositive();

        // No seat may appear in two successful reservations
        List<UUID> heldSeats = outcome.reservations().stream()
                .flatMap(id -> reservationService.get(id).seatIds().stream())
                .toList();
        assertThat(heldSeats).doesNotHaveDuplicates();
    }

    private Outcome race(java.util.function.IntFunction<Set<UUID>> seatsForThread) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();
        List<UUID> reservations = java.util.Collections.synchronizedList(new ArrayList<>());

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            int thread = i;
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    var result = reservationService.hold(eventId,
                            new HoldSeatsRequest("user-" + thread, seatsForThread.apply(thread)),
                            UUID.randomUUID().toString());
                    reservations.add(result.reservation().id());
                    succeeded.incrementAndGet();
                } catch (SeatsUnavailableException e) {
                    rejected.incrementAndGet();
                } catch (RuntimeException e) {
                    unexpected.incrementAndGet();
                    throw e;
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            try {
                f.get(30, TimeUnit.SECONDS);
            } catch (java.util.concurrent.ExecutionException ignored) {
                // counted in "unexpected"
            }
        }
        return new Outcome(succeeded.get(), rejected.get(), unexpected.get(), reservations);
    }

    private SeatStatus seatStatus(UUID seatId) {
        return eventService.seatMap(eventId).stream()
                .filter(s -> s.id().equals(seatId)).findFirst().orElseThrow().status();
    }

    private record Outcome(int succeeded, int rejected, int unexpected, List<UUID> reservations) {
    }
}
