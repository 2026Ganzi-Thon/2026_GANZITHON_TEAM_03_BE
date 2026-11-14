package com.ganzithon.maeum.concern;

import com.fasterxml.jackson.databind.JsonNode;
import com.ganzithon.maeum.global.activity.ActivityApi;

import java.util.Set;

/** 고민 작성 요청입니다. from()으로 만들 때 본문을 검증합니다. */
record ConcernCreateRequest(String content, String comfortType) {
    private static final int CONTENT_LIMIT = 1000;
    private static final Set<String> COMFORT_TYPES = Set.of("EMPATHY", "EXPERIENCE", "ADVICE");

    static ConcernCreateRequest from(JsonNode body) {
        ActivityApi.fields(body, "content", "comfortType");
        var c = body.get("content");
        var t = body.get("comfortType");
        if (c == null || !c.isTextual() || t == null || !t.isTextual()) throw ActivityApi.bad();
        String content = c.textValue().strip();
        String type = t.textValue();
        if (content.isEmpty()
                || content.codePointCount(0, content.length()) > CONTENT_LIMIT
                || !COMFORT_TYPES.contains(type)) throw ActivityApi.bad();
        return new ConcernCreateRequest(content, type);
    }
}
