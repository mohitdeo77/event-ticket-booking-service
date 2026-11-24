package com.mohitdeo.booking.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.reservation")
public record ReservationProperties(
        Duration holdTtl,
        int maxSeatsPerReservation,
        Duration idempotencyLockTtl,
        long sweepIntervalMs,
        int sweepBatchSize) {
}
