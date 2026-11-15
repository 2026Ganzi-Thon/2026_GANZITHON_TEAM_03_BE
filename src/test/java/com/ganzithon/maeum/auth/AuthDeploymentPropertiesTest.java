package com.ganzithon.maeum.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AuthDeploymentPropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withPropertyValues("spring.profiles.active=auth,smtp");

    @Test
    void frontendOriginSetsCorsAndEmailLinks() {
        runner.withPropertyValues("FRONTEND_ORIGIN=https://app.example.com").run(context -> {
            var environment = context.getEnvironment();
            assertThat(environment.getProperty("app.auth.allowed-origins")).isEqualTo("https://app.example.com");
            assertThat(environment.getProperty("app.verification.frontend-url"))
                    .isEqualTo("https://app.example.com/verify-email");
            assertThat(environment.getProperty("app.verification.password-reset-frontend-url"))
                    .isEqualTo("https://app.example.com/reset-password");
        });
    }

    @Test
    void explicitOriginsAndLinksTakePrecedence() {
        runner.withPropertyValues(
                "FRONTEND_ORIGIN=https://app.example.com",
                "CORS_ALLOWED_ORIGINS=https://app.example.com,https://preview.example.com",
                "EMAIL_VERIFICATION_FRONTEND_URL=https://app.example.com/account/verify",
                "PASSWORD_RESET_FRONTEND_URL=https://app.example.com/account/reset"
        ).run(context -> {
            var environment = context.getEnvironment();
            assertThat(environment.getProperty("app.auth.allowed-origins"))
                    .isEqualTo("https://app.example.com,https://preview.example.com");
            assertThat(environment.getProperty("app.verification.frontend-url"))
                    .isEqualTo("https://app.example.com/account/verify");
            assertThat(environment.getProperty("app.verification.password-reset-frontend-url"))
                    .isEqualTo("https://app.example.com/account/reset");
        });
    }
}
