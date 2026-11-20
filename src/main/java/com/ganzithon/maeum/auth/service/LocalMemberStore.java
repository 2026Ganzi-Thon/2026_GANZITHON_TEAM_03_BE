package com.ganzithon.maeum.auth.service;

import com.ganzithon.maeum.auth.domain.EmailAddress;
import com.ganzithon.maeum.auth.domain.LocalCredential;
import com.ganzithon.maeum.auth.repository.LocalCredentialRepository;
import com.ganzithon.maeum.user.domain.User;
import com.ganzithon.maeum.user.repository.UserRepository;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

/** 내부 저장 기반입니다. 가입 증명·동의 확인을 대신하는 공개 회원가입 서비스가 아닙니다. */
@Service
@Profile("!bootstrap")
public class LocalMemberStore {
    private final UserRepository users;
    private final LocalCredentialRepository credentials;
    private final PasswordService passwords;
    private final Validator validator;

    public LocalMemberStore(UserRepository users, LocalCredentialRepository credentials,
            PasswordService passwords, Validator validator) {
        this.users = users;
        this.credentials = credentials;
        this.passwords = passwords;
        this.validator = validator;
    }

    @Transactional
    public Long create(String email, String rawPassword) {
        return store(email, rawPassword, null).getId();
    }

    @Transactional
    public User createVerified(String email, String rawPassword, Instant verifiedAt) {
        return store(email, rawPassword, Objects.requireNonNull(verifiedAt));
    }

    private User store(String email, String rawPassword, Instant verifiedAt) {
        EmailAddress address = new EmailAddress(email);
        if (!validator.validate(address).isEmpty()) {
            throw new IllegalArgumentException("올바른 이메일을 입력해주세요.");
        }
        if (credentials.existsByEmail(address.value())) {
            throw new DuplicateLocalEmailException();
        }
        String hash = passwords.encodeForRegistration(rawPassword);
        User user = users.save(new User(address.value()));
        credentials.saveAndFlush(new LocalCredential(user, address, hash, verifiedAt));
        return user;
    }
}
