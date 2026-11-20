package com.ganzithon.maeum.auth.session;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

@Component
@Profile("auth")
public class AccessTokens {
    private final JwtEncoder encoder;
    private final Clock clock;
    public AccessTokens(@Qualifier("serviceJwtEncoder") JwtEncoder encoder, Clock clock) {
        this.encoder = encoder;
        this.clock = clock;
    }
    public String issue(long userId, String sessionId) {
        var now = clock.instant();
        var claims = JwtClaimsSet.builder().issuer("maeum-backend").subject(Long.toString(userId))
                .audience(List.of("maeum-api")).issuedAt(now).expiresAt(now.plusSeconds(3600))
                .id(UUID.randomUUID().toString()).claim("sid", sessionId).claim("token_use", "access").build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
