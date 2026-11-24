package com.mohitdeo.booking.hold;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis-backed seat holds.
 * <ul>
 *   <li>Holding several seats is all-or-nothing: a Lua script checks and sets every key in
 *       one atomic step, so a request never ends up holding only some of its seats.</li>
 *   <li>Each key gets a TTL (PX), so a hold left by a crashed request expires by itself.</li>
 *   <li>Release is compare-and-delete: a key is removed only if it still holds our reservation
 *       id, so a slow request cannot delete a hold that someone else took later.</li>
 *   <li>Keys share the {eventId} hash tag, so one multi-key script still works on Redis Cluster.</li>
 *   <li>If Redis is down, holds fall back to the database locks alone. Correctness does not depend on Redis.</li>
 * </ul>
 */
@Component
public class RedisSeatHoldStore implements SeatHoldStore {

    private static final Logger log = LoggerFactory.getLogger(RedisSeatHoldStore.class);

    private static final RedisScript<Long> HOLD_ALL = new DefaultRedisScript<>("""
            for _, key in ipairs(KEYS) do
              if redis.call('EXISTS', key) == 1 then
                return 0
              end
            end
            for _, key in ipairs(KEYS) do
              redis.call('SET', key, ARGV[1], 'PX', ARGV[2])
            end
            return 1
            """, Long.class);

    private static final RedisScript<Long> RELEASE_OWNED = new DefaultRedisScript<>("""
            local released = 0
            for _, key in ipairs(KEYS) do
              if redis.call('GET', key) == ARGV[1] then
                redis.call('DEL', key)
                released = released + 1
              end
            end
            return released
            """, Long.class);

    private final StringRedisTemplate redis;

    public RedisSeatHoldStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean tryHoldAll(UUID eventId, Collection<UUID> seatIds, UUID reservationId, Duration ttl) {
        try {
            Long result = redis.execute(HOLD_ALL, keys(eventId, seatIds),
                    reservationId.toString(), String.valueOf(ttl.toMillis()));
            return result != null && result == 1L;
        } catch (DataAccessException e) {
            log.warn("Redis unavailable, falling back to database locking for event {}", eventId, e);
            return true;
        }
    }

    @Override
    public void releaseAll(UUID eventId, Collection<UUID> seatIds, UUID reservationId) {
        try {
            redis.execute(RELEASE_OWNED, keys(eventId, seatIds), reservationId.toString());
        } catch (DataAccessException e) {
            // Safe to ignore: the key TTL will clean the hold up shortly
            log.warn("Could not release Redis holds for reservation {}", reservationId, e);
        }
    }

    static List<String> keys(UUID eventId, Collection<UUID> seatIds) {
        return seatIds.stream().sorted().map(seatId -> "seat-hold:{" + eventId + "}:" + seatId).toList();
    }
}
