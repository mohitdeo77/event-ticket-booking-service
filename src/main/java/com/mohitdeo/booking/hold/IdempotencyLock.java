package com.mohitdeo.booking.hold;

import java.time.Duration;

/**
 * Stops two requests with the same Idempotency-Key from running at the same time,
 * for example when a client retries before the first attempt has finished.
 */
public interface IdempotencyLock {

    boolean tryLock(String idempotencyKey, Duration ttl);

    void unlock(String idempotencyKey);
}
