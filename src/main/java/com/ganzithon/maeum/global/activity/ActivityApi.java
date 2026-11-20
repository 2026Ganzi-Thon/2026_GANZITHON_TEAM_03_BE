package com.ganzithon.maeum.global.activity;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.*;
import java.util.*;

/** Shared wire format and explicit JSON validation, including PATCH absent/null semantics. */
public final class ActivityApi {
    private ActivityApi() {}

    public static final class Problem extends RuntimeException {
        final int status;
        final String code;

        Problem(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }

    public static Problem bad() {
        return new Problem(400, "COMMON_INVALID_REQUEST", "입력값을 확인해주세요.");
    }

    public static Problem problem(int status, String code) {
        return new Problem(
                status,
                code,
                switch (status) {
                    case 404 -> "요청한 데이터를 찾을 수 없습니다.";
                    case 409 -> "현재 상태에서는 요청을 처리할 수 없습니다.";
                    default -> "입력값을 확인해주세요.";
                });
    }

    public static Map<String, Object> obj(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }

    public static long id(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) throw bad();
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw bad();
        }
    }

    public static void fields(JsonNode body, String... allowed) {
        if (body == null || !body.isObject()) throw bad();
        Set<String> names = Set.of(allowed);
        body.fieldNames()
                .forEachRemaining(
                        n -> {
                            if (!names.contains(n)) throw bad();
                        });
    }

    public static void noBody(JsonNode body) {
        if (body != null) throw bad();
    }

    public static String memo(JsonNode value, int limit) {
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw bad();
        String s = value.textValue();
        if (s.codePointCount(0, s.length()) > limit) throw bad();
        return s.isBlank() ? null : s;
    }

    public static LocalDate today(Clock clock) {
        return day(clock.instant());
    }

    public static LocalDate day(Instant now) {
        return LocalDate.ofInstant(now, ZoneId.of("Asia/Seoul"));
    }

    public static LocalDateTime ts(Instant i) {
        return LocalDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    public static Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime t) return t.toInstant(ZoneOffset.UTC);
        if (value instanceof java.sql.Timestamp t)
            return t.toLocalDateTime().toInstant(ZoneOffset.UTC);
        throw new IllegalStateException("Unsupported database timestamp");
    }

    public static boolean bool(Object v) {
        return v instanceof Boolean b ? b : ((Number) v).intValue() != 0;
    }

    public static String sid(Object value) {
        return value == null ? null : value.toString();
    }

    public static void page(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE)
            throw bad();
    }

    public static Map<String, Object> pageResult(List<?> rows, int page, int size) {
        boolean next = rows.size() > size;
        return obj(
                "items",
                rows.subList(0, Math.min(size, rows.size())),
                "page",
                page,
                "size",
                size,
                "hasNext",
                next);
    }
}
