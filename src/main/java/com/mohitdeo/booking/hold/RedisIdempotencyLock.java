package com.mohitdeo.booking.hold;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * SET NX with a TTL, so a crashed request cannot leave its key locked forever. If Redis
 * is down the lock is skipped; the unique constraint on reservations.idempotency_key
 * still stops duplicate inserts.
 */
@Component
public class RedisIdempotencyLock implements IdempotencyLock {

    private static final Logger log = LoggerFactory.getLogger(RedisIdempotencyLock.class);

    private final StringRedisTemplate redis;

    public RedisIdempotencyLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean tryLock(String idempotencyKey, Duration ttl) {
        try {
            return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key(idempotencyKey), "in-progress", ttl));
        } catch (DataAccessException e) {
            log.warn("Redis unavailable, relying on database unique constraint for idempotency", e);
            return true;
        }
    }

    @Override
    public void unlock(String idempotencyKey) {
        try {
            redis.delete(key(idempotencyKey));
        } catch (DataAccessException e) {
            log.warn("Could not release idempotency lock {}", idempotencyKey, e);
        }
    }

    private static String key(String idempotencyKey) {
        return "idempotency:" + idempotencyKey;
    }
}
