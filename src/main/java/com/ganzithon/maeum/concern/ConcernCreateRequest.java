package com.ganzithon.maeum.concern;

import com.fasterxml.jackson.databind.JsonNode;
import com.ganzithon.maeum.global.activity.ActivityApi;

import java.util.Set;

/**
 * 고민 작성 요청입니다. from()으로 만들 때 제목과 본문을 검증합니다. 제목 필드가 없으면 기존 화면과의 호환을 위해 본문 첫 줄(최대
 * 50자, 비면 "제목 없음")로 채웁니다. V10이 기존 고민을 채운 규칙과 같습니다.
 */
record ConcernCreateRequest(String title, String content, ComfortType comfortType) {
    private static final int TITLE_LIMIT = 50;
    private static final int CONTENT_LIMIT = 1000;
    private static final Set<String> COMFORT_TYPES = Set.of("EMPATHY", "EXPERIENCE", "ADVICE");

    static ConcernCreateRequest from(JsonNode body) {
        ActivityApi.fields(body, "title", "content", "comfortType");
        var ti = body.get("title");
        var c = body.get("content");
        var t = body.get("comfortType");
        if ((ti != null && !ti.isTextual())
                || c == null
                || !c.isTextual()
                || t == null
                || !t.isTextual()) throw ActivityApi.bad();
        String content = c.textValue().strip();
        String title = ti == null ? firstLine(content) : ti.textValue().strip();
        String type = t.textValue();
        if (title.isEmpty()
                || title.codePointCount(0, title.length()) > TITLE_LIMIT
                || content.isEmpty()
                || content.codePointCount(0, content.length()) > CONTENT_LIMIT
                || !COMFORT_TYPES.contains(type)) throw ActivityApi.bad();
        return new ConcernCreateRequest(title, content, ComfortType.valueOf(type));
    }

    private static String firstLine(String content) {
        String line = content.split("\n", 2)[0].strip();
        int end = line.offsetByCodePoints(0, Math.min(TITLE_LIMIT, line.codePointCount(0, line.length())));
        line = line.substring(0, end).strip();
        return line.isEmpty() ? "제목 없음" : line;
    }
}
