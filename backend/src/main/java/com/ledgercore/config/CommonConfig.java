package com.ledgercore.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class CommonConfig {

    /** A UTC clock, injected wherever time is needed so tests can substitute a fixed clock. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
