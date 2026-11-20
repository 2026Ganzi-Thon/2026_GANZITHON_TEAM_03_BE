package com.ganzithon.maeum.auth;

import com.ganzithon.maeum.auth.verification.VerificationTokens;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VerificationTokensTest {
    @Test
    void tokensAreIndependentUrlSafeAndStoredAsHashes() {
        var tokens = new VerificationTokens();
        String first = tokens.issue();
        assertThat(first).matches("[A-Za-z0-9_-]{43}");
        assertThat(tokens.issue()).isNotEqualTo(first);
        assertThat(tokens.hash(first)).matches("[0-9a-f]{64}").isNotEqualTo(first);
        assertThat(tokens.hash(first)).isEqualTo(tokens.hash(first));
    }
}
