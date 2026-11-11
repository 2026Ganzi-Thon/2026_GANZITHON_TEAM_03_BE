package com.ganzithon.maeum.auth.session;

import com.ganzithon.maeum.auth.google.*;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

@Configuration
@Profile("auth")
@EnableConfigurationProperties(AuthSettings.class)
public class AuthConfiguration {
    @Bean
    SecretKey serviceSigningKey(AuthSettings settings) {
        try {
            byte[] bytes = Base64.getDecoder().decode(settings.jwtSecretBase64());
            if (bytes.length < 32) throw new IllegalArgumentException();
            return new SecretKeySpec(bytes, "HmacSHA256");
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("JWT_SECRET_BASE64에는 32바이트 이상의 무작위 키를 Base64로 설정해주세요.");
        }
    }

    @Bean
    JwtEncoder serviceJwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    JwtDecoder serviceJwtDecoder(SecretKey key, AuthSessionService sessions, Clock clock) {
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(jwt -> {
            if (!"maeum-backend".equals(jwt.getClaimAsString("iss")) || jwt.getAudience() == null
                    || !jwt.getAudience().contains("maeum-api") || !"access".equals(jwt.getClaimAsString("token_use"))
                    || jwt.getSubject() == null || !jwt.getSubject().matches("[1-9][0-9]*")
                    || jwt.getClaimAsString("sid") == null || jwt.getExpiresAt() == null || jwt.getIssuedAt() == null
                    || jwt.getIssuedAt().isAfter(clock.instant().plusSeconds(30))
                    || (jwt.getNotBefore() != null && clock.instant().isBefore(jwt.getNotBefore()))) return invalid("AUTH_ACCESS_TOKEN_INVALID");
            if (!clock.instant().isBefore(jwt.getExpiresAt())) return invalid("AUTH_ACCESS_TOKEN_EXPIRED");
            if (!sessions.active(jwt.getClaimAsString("sid"), jwt.getSubject())) return invalid("AUTH_ACCESS_TOKEN_INVALID");
            return OAuth2TokenValidatorResult.success();
        });
        return decoder;
    }

    @Bean
    GoogleIdentityVerifier googleIdentityVerifier(AuthSettings settings, Clock clock, Validator validator) {
        var decoder = NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs").build();
        var timestamps = new JwtTimestampValidator(Duration.ofSeconds(30));
        timestamps.setClock(clock);
        decoder.setJwtValidator(timestamps);
        return new GoogleJwtVerifier(decoder, clock, settings.googleClientId(), validator);
    }

    private OAuth2TokenValidatorResult invalid(String code) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(code, "Access Token을 확인해주세요.", null));
    }
}
