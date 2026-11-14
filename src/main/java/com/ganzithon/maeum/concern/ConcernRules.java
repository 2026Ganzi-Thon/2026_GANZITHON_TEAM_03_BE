package com.ganzithon.maeum.concern;

import com.ganzithon.maeum.global.activity.ActivityApi;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/** 고민 서비스들이 함께 쓰는 상태 계산과 미리보기 규칙입니다. */
final class ConcernRules {
    private static final int PREVIEW_LENGTH = 100;

    private ConcernRules() {}

    static Instant now(Clock clock) {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    static String status(Map<String, Object> r, Instant now) {
        if (r.get("saved_at") != null) return "SAVED";
        if (r.get("opened_at") != null) return "OPENED";
        return now.isBefore(ActivityApi.instant(r.get("matures_at"))) ? "MATURING" : "MATURED";
    }

    static String preview(Map<String, Object> r) {
        String content = (String) r.get("content");
        int end =
                content.offsetByCodePoints(
                        0, Math.min(PREVIEW_LENGTH, content.codePointCount(0, content.length())));
        return content.substring(0, end);
    }
}
