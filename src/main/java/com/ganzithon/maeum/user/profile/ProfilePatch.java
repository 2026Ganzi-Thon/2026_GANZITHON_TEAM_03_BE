package com.ganzithon.maeum.user.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

record ProfilePatch(Map<String, String> values) {
    private static final Set<String> FIELDS = Set.of("nickname", "introduction", "ageGroup", "regionCode");
    private static final Pattern NICKNAME = Pattern.compile("[가-힣A-Za-z0-9_]{2,8}");

    static ProfilePatch parse(ObjectNode body, boolean initial) {
        if (body.isEmpty()) throw ProfileException.invalid("body", "수정할 항목을 입력해주세요.");
        if (initial && !body.has("nickname")) throw ProfileException.invalid("nickname", "닉네임을 입력해주세요.");

        Map<String, String> values = new HashMap<>();
        body.properties().forEach(field -> {
            String name = field.getKey();
            JsonNode node = field.getValue();
            if (!FIELDS.contains(name)) throw ProfileException.invalid(name, "수정할 수 없는 항목입니다.");
            if (!node.isTextual() && !node.isNull()) {
                throw ProfileException.invalid(name, "문자열 또는 null을 입력해주세요.");
            }
            String value = node.textValue();
            values.put(name, switch (name) {
                case "nickname" -> nickname(value);
                case "introduction" -> bounded(name, value, 200);
                default -> code(name, value);
            });
        });
        return new ProfilePatch(values);
    }

    static String nickname(String raw) {
        String value = raw == null ? "" : raw.strip();
        if (!NICKNAME.matcher(value).matches()) {
            throw ProfileException.invalid("nickname", "닉네임은 한글, 영문, 숫자, 밑줄로 2~8자를 입력해주세요.");
        }
        return value;
    }

    String apply(String field, String current) {
        return values.getOrDefault(field, current);
    }

    boolean matches(MemberProfileService.ProfileView current) {
        return Objects.equals(apply("nickname", current.nickname()), current.nickname())
                && Objects.equals(apply("introduction", current.introduction()), current.introduction())
                && Objects.equals(apply("ageGroup", current.ageGroup()), current.ageGroup())
                && Objects.equals(apply("regionCode", current.regionCode()), current.regionCode());
    }

    private static String code(String field, String value) {
        if (value == null) return null;
        String stripped = value.strip();
        if (stripped.isEmpty()) throw ProfileException.invalid(field, "빈 코드는 사용할 수 없습니다.");
        return bounded(field, stripped, 30);
    }

    private static String bounded(String field, String value, int limit) {
        if (value != null && value.codePointCount(0, value.length()) > limit) {
            throw ProfileException.invalid(field, "최대 " + limit + "자까지 입력할 수 있습니다.");
        }
        return value;
    }
}
