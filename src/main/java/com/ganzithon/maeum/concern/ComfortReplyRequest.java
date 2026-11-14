package com.ganzithon.maeum.concern;

import com.fasterxml.jackson.databind.JsonNode;
import com.ganzithon.maeum.global.activity.ActivityApi;

/** 위로 작성 요청입니다. from()으로 만들 때 본문을 검증합니다. */
record ComfortReplyRequest(String content) {
    private static final int REPLY_LIMIT = 500;

    static ComfortReplyRequest from(JsonNode body) {
        ActivityApi.fields(body, "content");
        var c = body.get("content");
        if (c == null || !c.isTextual()) throw ActivityApi.bad();
        String content = c.textValue().strip();
        if (content.isEmpty() || content.codePointCount(0, content.length()) > REPLY_LIMIT)
            throw ActivityApi.bad();
        return new ComfortReplyRequest(content);
    }
}
