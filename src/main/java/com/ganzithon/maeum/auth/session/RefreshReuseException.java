package com.ganzithon.maeum.auth.session;

import org.springframework.http.HttpStatus;

public class RefreshReuseException extends AuthFault {
    public RefreshReuseException() {
        super(HttpStatus.UNAUTHORIZED, "AUTH_REFRESH_TOKEN_REUSED", "로그인이 만료되었습니다. 다시 로그인해주세요.");
    }
}
