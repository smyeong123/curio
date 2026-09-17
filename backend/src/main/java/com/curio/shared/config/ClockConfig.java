package com.curio.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * The application clock. Time-gated code (delivery-hour checks, "today's digest")
 * takes this through its constructor so tests can pin the moment with
 * {@link Clock#fixed}; production runs on UTC to match how every timestamp is stored.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
