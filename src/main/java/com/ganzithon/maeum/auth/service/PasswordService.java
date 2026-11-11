package com.ganzithon.maeum.auth.service;

import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {
    private static final Pattern SPECIAL = Pattern.compile("[\\p{P}\\p{S}]");
    private final PasswordEncoder encoder;

    public PasswordService(PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    public String encodeForRegistration(String password) {
        if (password == null) {
            throw new RegistrationPasswordException("비밀번호를 입력해주세요.");
        }
        int length = password.codePointCount(0, password.length());
        if (length < 8 || length > 64
                || password.chars().noneMatch(c -> c >= 'A' && c <= 'Z')
                || password.chars().noneMatch(c -> c >= '0' && c <= '9')
                || !SPECIAL.matcher(password).find()) {
            throw new RegistrationPasswordException("비밀번호는 8~64자이며 영문 대문자, 숫자, 특수문자를 포함해야 합니다.");
        }
        return encoder.encode(password);
    }

    public boolean matches(String password, String encodedPassword) {
        if (password == null || encodedPassword == null) {
            return false;
        }
        // 로그인 때 가입 정책을 다시 강제하거나 원문 공백을 제거하지 않습니다.
        return encoder.matches(password, encodedPassword);
    }
}
