package com.ganzithon.maeum.global.activity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.ganzithon.maeum.global.api.ApiResponse;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

/** Called inside the domain transaction, AFTER taking the user's database row lock. */
@Component
@Profile("auth")
public class Idempotency {
    public record Reply(int status, Object body) {}

    private final ActivityStore store;
    private final ObjectMapper mapper;

    Idempotency(ActivityStore store, ObjectMapper mapper) {
        this.store = store;
        this.mapper = mapper;
    }

    public Reply execute(
            long user,
            String key,
            String operation,
            JsonNode body,
            Instant now,
            int status,
            Supplier<Object> action) {
        if (key == null || !key.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"))
            throw ActivityApi.bad();
        key = UUID.fromString(key).toString();
        String fingerprint = hash(operation + "\n" + (body == null ? "" : body.toString()));
        var old =
                store.one(
                        "SELECT * FROM api_idempotency_requests WHERE user_id=? AND request_key=?",
                        user,
                        key);
        if (old != null && ActivityApi.instant(old.get("expires_at")).isAfter(now)) {
            if (!fingerprint.equals(old.get("request_hash")))
                throw ActivityApi.problem(409, "COMMON_IDEMPOTENCY_CONFLICT");
            try {
                return new Reply(
                        ((Number) old.get("response_status")).intValue(),
                        mapper.readTree((String) old.get("response_body")));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Stored idempotency response is invalid", e);
            }
        }
        if (old != null)
            store.update(
                    "DELETE FROM api_idempotency_requests WHERE user_id=? AND request_key=?",
                    user,
                    key);
        Object result = new ApiResponse<>(action.get());
        try {
            store.update(
                    "INSERT INTO"
                        + " api_idempotency_requests(user_id,request_key,request_hash,response_status,response_body,expires_at)"
                        + " VALUES(?,?,?,?,?,?)",
                    user,
                    key,
                    fingerprint,
                    status,
                    mapper.writeValueAsString(result),
                    ActivityApi.ts(now.plus(Duration.ofHours(24))));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return new Reply(status, result);
    }

    private String hash(String text) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
