package com.ganzithon.maeum.auth.service;

public class DuplicateLocalEmailException extends RuntimeException {
    public DuplicateLocalEmailException() {
        super("이미 사용 중인 이메일입니다.");
    }
}
