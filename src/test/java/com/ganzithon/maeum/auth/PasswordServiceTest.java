package com.ganzithon.maeum.auth;

import com.ganzithon.maeum.auth.service.PasswordService;
import com.ganzithon.maeum.global.config.PasswordConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class PasswordServiceTest {
    private final PasswordService passwords = new PasswordService(new PasswordConfig().passwordEncoder());

    @Test
    void hashesHaveIndependentSaltAndVerifyExactInput() {
        String raw = " Abcdef1! ";
        String first = passwords.encodeForRegistration(raw);
        assertThat(first).startsWith("{pbkdf2-sha256-600k}").isNotEqualTo(raw);
        assertThat(passwords.encodeForRegistration(raw)).isNotEqualTo(first);
        assertThat(passwords.matches(raw, first)).isTrue();
        assertThat(passwords.matches(raw.strip(), first)).isFalse();
        assertThat(passwords.matches("Wrong123!", first)).isFalse();
    }

    @Test
    void accepts64UnicodeCodePointsWithoutByteTruncation() {
        String raw = "A1!" + "가".repeat(61);
        String hash = passwords.encodeForRegistration(raw);
        assertThat(passwords.matches(raw, hash)).isTrue();
        assertThat(passwords.matches(raw.substring(0, raw.length() - 1) + "나", hash)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Ab1!", "abcdef1!", "Abcdefg!", "Abcdef12"})
    void rejectsInvalidRegistrationPasswords(String raw) {
        assertThatIllegalArgumentException().isThrownBy(() -> passwords.encodeForRegistration(raw));
    }

    @Test
    void rejectsMoreThan64Characters() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> passwords.encodeForRegistration("A1!" + "a".repeat(62)));
    }
}
