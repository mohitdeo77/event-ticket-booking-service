package com.mohitdeo.booking.reservation;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.mohitdeo.booking.reservation.ReservationDtos.HoldResult;
import com.mohitdeo.booking.reservation.ReservationDtos.HoldSeatsRequest;
import com.mohitdeo.booking.reservation.ReservationDtos.ReservationView;

@RestController
public class ReservationController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /**
     * Holds seats for a limited time. Returns 201 for a new hold, or 200 with
     * {@code Idempotent-Replayed: true} when the same request is sent again.
     */
    @PostMapping("/api/events/{eventId}/reservations")
    public ResponseEntity<ReservationView> hold(
            @PathVariable UUID eventId,
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody HoldSeatsRequest request) {

        HoldResult result = reservationService.hold(eventId, request, idempotencyKey);
        ReservationView body = result.reservation();
        if (!result.created()) {
            return ResponseEntity.ok().header(IDEMPOTENT_REPLAYED, "true").body(body);
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/reservations/" + body.id()))
                .body(body);
    }

    @GetMapping("/api/reservations/{reservationId}")
    public ReservationView get(@PathVariable UUID reservationId) {
        return reservationService.get(reservationId);
    }

    @PostMapping("/api/reservations/{reservationId}/confirm")
    public ReservationView confirm(@PathVariable UUID reservationId) {
        return reservationService.confirm(reservationId);
    }

    @PostMapping("/api/reservations/{reservationId}/cancel")
    public ReservationView cancel(@PathVariable UUID reservationId) {
        return reservationService.cancel(reservationId);
    }
}
