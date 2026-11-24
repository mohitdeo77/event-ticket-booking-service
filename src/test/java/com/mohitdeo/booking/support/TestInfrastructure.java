package com.mohitdeo.booking.support;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.mohitdeo.booking.hold.IdempotencyLock;

/** Replaces Redis and the system clock with in-memory versions the tests can control. */
@TestConfiguration
public class TestInfrastructure {

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
    }

    @Bean
    @Primary
    InMemorySeatHoldStore inMemorySeatHoldStore(MutableClock testClock) {
        return new InMemorySeatHoldStore(testClock);
    }

    @Bean
    @Primary
    IdempotencyLock inMemoryIdempotencyLock() {
        Set<String> locked = ConcurrentHashMap.newKeySet();
        return new IdempotencyLock() {
            @Override
            public boolean tryLock(String key, Duration ttl) {
                return locked.add(key);
            }

            @Override
            public void unlock(String key) {
                locked.remove(key);
            }
        };
    }
}
