package com.ganzithon.maeum.auth.domain;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/** 일반 로그인 식별자. 연락용 이메일 및 소셜 계정 식별자와 별개입니다. */
public record EmailAddress(@NotBlank @Email @Size(max = 255) String value) {
    public EmailAddress {
        if (value != null) {
            value = value.strip().toLowerCase(Locale.ROOT);
        }
    }
}
