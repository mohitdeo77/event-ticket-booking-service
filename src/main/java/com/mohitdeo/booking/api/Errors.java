package com.mohitdeo.booking.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.mohitdeo.booking.reservation.ReservationStatus;

/** All business exceptions in one place, so the API's error contract is easy to review. */
public final class Errors {

    private Errors() {
    }

    public static class NotFoundException extends BookingException {
        public NotFoundException(String resource, Object id) {
            super(HttpStatus.NOT_FOUND, resource + " not found", resource + " " + id + " does not exist");
        }
    }

    public static class InvalidRequestException extends BookingException {
        public InvalidRequestException(String detail) {
            super(HttpStatus.BAD_REQUEST, "Invalid request", detail);
        }
    }

    public static class SeatsUnavailableException extends BookingException {
        private final List<UUID> seatIds;

        public SeatsUnavailableException(Collection<UUID> seatIds) {
            super(HttpStatus.CONFLICT, "Seats unavailable",
                    "One or more requested seats are already held or booked");
            this.seatIds = List.copyOf(seatIds);
        }

        @Override
        public Map<String, Object> properties() {
            return Map.of("seatIds", seatIds);
        }
    }

    public static class ReservationExpiredException extends BookingException {
        public ReservationExpiredException(UUID reservationId) {
            super(HttpStatus.CONFLICT, "Reservation expired",
                    "Hold " + reservationId + " expired and its seats were released");
        }
    }

    public static class InvalidReservationStateException extends BookingException {
        public InvalidReservationStateException(UUID reservationId, ReservationStatus from, ReservationStatus to) {
            super(HttpStatus.CONFLICT, "Invalid reservation state",
                    "Reservation " + reservationId + " cannot move from " + from + " to " + to);
        }
    }

    public static class IdempotencyKeyReusedException extends BookingException {
        public IdempotencyKeyReusedException(String key) {
            super(HttpStatus.UNPROCESSABLE_CONTENT, "Idempotency key reused",
                    "Idempotency-Key '" + key + "' was already used with a different request body");
        }
    }

    public static class RequestInProgressException extends BookingException {
        public RequestInProgressException(String key) {
            super(HttpStatus.CONFLICT, "Request in progress",
                    "A request with Idempotency-Key '" + key + "' is still being processed; retry shortly");
        }
    }
}
