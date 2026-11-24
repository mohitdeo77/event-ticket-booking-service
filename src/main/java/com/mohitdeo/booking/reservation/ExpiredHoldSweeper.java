package com.mohitdeo.booking.reservation;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Releases expired holds on a schedule. The Redis TTL already frees the fast gate, but the
 * database is the source of truth, so its seat rows must be released too, even if the
 * client disappeared or Redis lost the key.
 */
@Component
class ExpiredHoldSweeper {

    private final ReservationService reservationService;

    ExpiredHoldSweeper(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Scheduled(fixedDelayString = "${app.reservation.sweep-interval-ms}")
    void sweep() {
        reservationService.expireDueHolds();
    }
}
