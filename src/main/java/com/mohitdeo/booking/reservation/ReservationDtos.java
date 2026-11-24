package com.mohitdeo.booking.reservation;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ReservationDtos {

    private ReservationDtos() {
    }

    public record HoldSeatsRequest(
            @NotBlank @Size(max = 100) String userId,
            @NotEmpty Set<@NotNull UUID> seatIds) {
    }

    public record ReservationView(
            UUID id,
            UUID eventId,
            String userId,
            ReservationStatus status,
            List<UUID> seatIds,
            Instant expiresAt) {

        static ReservationView from(Reservation r) {
            return new ReservationView(r.getId(), r.getEventId(), r.getUserId(), r.getStatus(),
                    r.getSeatIds().stream().sorted().toList(), r.getExpiresAt());
        }
    }

    /** {@code created == false} means the response replays an earlier request with the same Idempotency-Key. */
    public record HoldResult(ReservationView reservation, boolean created) {
    }
}
