package com.ganzithon.maeum.auth;

import com.ganzithon.maeum.auth.google.GoogleJwtVerifier;
import com.ganzithon.maeum.auth.session.AuthFault;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import jakarta.validation.Validation;
import java.time.*;
import java.util.Date;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import static org.assertj.core.api.Assertions.*;

class GoogleJwtVerifierTest {
    static RSAKey key;
    GoogleJwtVerifier verifier;
    final Instant now = Instant.now();
    @BeforeAll static void keys() throws Exception { key = new RSAKeyGenerator(2048).generate(); }
    @BeforeEach void setup() throws Exception {
        var decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
        verifier = new GoogleJwtVerifier(decoder, Clock.fixed(now, ZoneOffset.UTC), "client-id",
                Validation.buildDefaultValidatorFactory().getValidator());
    }
    String token(String issuer, String audience, String nonce, boolean verified, Instant expiry, RSAKey signer) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(issuer).audience(audience).subject("google-sub")
                .issueTime(Date.from(now)).expirationTime(Date.from(expiry)).claim("email", "Member@example.com")
                .claim("email_verified", verified).claim("nonce", nonce).build();
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(signer));
        return jwt.serialize();
    }
    @Test void verifiesSignatureAndUsesSubjectAsIdentity() throws Exception {
        var identity = verifier.verify(token("https://accounts.google.com", "client-id", "nonce", true, now.plusSeconds(300), key), "nonce");
        assertThat(identity.subject()).isEqualTo("google-sub");
        assertThat(identity.email()).isEqualTo("member@example.com");
    }
    @Test void rejectsWrongIssuerAudienceNonceAndUnverifiedEmail() throws Exception {
        for (String[] fields : new String[][] {{"attacker", "client-id", "nonce", "true"},
                {"accounts.google.com", "other-app", "nonce", "true"},
                {"accounts.google.com", "client-id", "other-nonce", "true"},
                {"accounts.google.com", "client-id", "nonce", "false"}}) {
            String raw = token(fields[0], fields[1], fields[2], Boolean.parseBoolean(fields[3]), now.plusSeconds(300), key);
            assertThatThrownBy(() -> verifier.verify(raw, "nonce")).isInstanceOf(AuthFault.class);
        }
    }
    @Test void rejectsExpiredAndForgedTokens() throws Exception {
        String expired = token("accounts.google.com", "client-id", "nonce", true, now.minusSeconds(1), key);
        String forged = token("accounts.google.com", "client-id", "nonce", true, now.plusSeconds(300), new RSAKeyGenerator(2048).generate());
        assertThatThrownBy(() -> verifier.verify(expired, "nonce")).isInstanceOf(AuthFault.class);
        assertThatThrownBy(() -> verifier.verify(forged, "nonce")).isInstanceOf(AuthFault.class);
    }
}
