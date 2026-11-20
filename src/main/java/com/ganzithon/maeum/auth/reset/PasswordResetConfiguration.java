package com.ganzithon.maeum.auth.reset;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@Profile("auth")
class PasswordResetConfiguration {
    @Bean(defaultCandidate = false)
    ThreadPoolTaskExecutor passwordResetExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("password-reset-");
        return executor;
    }
}
