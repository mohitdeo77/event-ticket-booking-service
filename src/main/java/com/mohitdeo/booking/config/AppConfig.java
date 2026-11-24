package com.mohitdeo.booking.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
public class AppConfig {

    /** Injected everywhere time matters, so tests can move time forward deterministically. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
    static class SchedulingConfig {
    }
}
