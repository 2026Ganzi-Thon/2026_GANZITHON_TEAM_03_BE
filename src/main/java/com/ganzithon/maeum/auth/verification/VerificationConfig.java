package com.ganzithon.maeum.auth.verification;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VerificationConfig {
    @Bean
    public Clock verificationClock() {
        return Clock.systemUTC();
    }
}
