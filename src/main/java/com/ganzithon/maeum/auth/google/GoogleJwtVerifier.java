package com.ganzithon.maeum.auth.google;

import com.ganzithon.maeum.auth.domain.EmailAddress;
import com.ganzithon.maeum.auth.session.AuthFault;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtDecoder;

public class GoogleJwtVerifier implements GoogleIdentityVerifier {
    private final JwtDecoder decoder;
    private final Clock clock;
    private final String clientId;
    private final Validator validator;
    public GoogleJwtVerifier(JwtDecoder decoder, Clock clock, String clientId, Validator validator) {
        this.decoder = decoder; this.clock = clock; this.clientId = clientId; this.validator = validator;
    }
    @Override
    public Identity verify(String credential, String expectedNonce) {
        try {
            var jwt = decoder.decode(credential);
            String issuer = jwt.getClaimAsString("iss");
            String nonce = jwt.getClaimAsString("nonce");
            String subject = jwt.getSubject();
            var email = new EmailAddress(jwt.getClaimAsString("email"));
            if (!("https://accounts.google.com".equals(issuer) || "accounts.google.com".equals(issuer))
                    || !jwt.getAudience().contains(clientId) || jwt.getExpiresAt() == null
                    || !clock.instant().isBefore(jwt.getExpiresAt()) || jwt.getIssuedAt() == null
                    || jwt.getIssuedAt().isAfter(clock.instant().plusSeconds(30))
                    || subject == null || subject.isBlank() || subject.length() > 255
                    || !validator.validate(email).isEmpty() || !Boolean.TRUE.equals(jwt.getClaim("email_verified"))
                    || nonce == null || expectedNonce == null
                    || !MessageDigest.isEqual(nonce.getBytes(StandardCharsets.UTF_8), expectedNonce.getBytes(StandardCharsets.UTF_8))) {
                throw invalid();
            }
            String picture = jwt.getClaimAsString("picture");
            if (picture != null && (!picture.startsWith("https://") || picture.length() > 1000)) picture = null;
            return new Identity(subject, email.value(), picture);
        } catch (RuntimeException exception) {
            throw invalid();
        }
    }
    private AuthFault invalid() { return new AuthFault(HttpStatus.UNAUTHORIZED, "AUTH_GOOGLE_TOKEN_INVALID", "구글 인증을 확인하지 못했습니다. 다시 시도해주세요."); }
}
