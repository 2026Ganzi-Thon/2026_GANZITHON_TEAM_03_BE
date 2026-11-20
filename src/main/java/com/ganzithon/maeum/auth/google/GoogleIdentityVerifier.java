package com.ganzithon.maeum.auth.google;

public interface GoogleIdentityVerifier {
    Identity verify(String credential, String expectedNonce);
    record Identity(String subject, String email, String picture) { }
}
