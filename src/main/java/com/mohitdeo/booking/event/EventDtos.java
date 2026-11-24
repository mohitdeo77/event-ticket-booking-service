package com.mohitdeo.booking.event;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.mohitdeo.booking.seat.Seat;
import com.mohitdeo.booking.seat.SeatStatus;

public final class EventDtos {

    private EventDtos() {
    }

    public record CreateEventRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 200) String venue,
            @NotNull Instant startsAt,
            @Min(1) @Max(26) int rows,
            @Min(1) @Max(100) int seatsPerRow) {
    }

    public record EventView(UUID id, String name, String venue, Instant startsAt, int totalSeats) {
        static EventView from(Event event, int totalSeats) {
            return new EventView(event.getId(), event.getName(), event.getVenue(), event.getStartsAt(), totalSeats);
        }
    }

    public record SeatView(UUID id, String row, int number, SeatStatus status) {
        static SeatView from(Seat seat) {
            return new SeatView(seat.getId(), seat.getRowLabel(), seat.getSeatNumber(), seat.getStatus());
        }
    }
}
