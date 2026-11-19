package com.ganzithon.maeum.concern;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** 고민 서비스들이 함께 쓰는 시각 규칙입니다. 상태·미리보기 규칙은 {@link Concern}에 있습니다. */
final class ConcernRules {
    private ConcernRules() {}

    /** DB의 DATETIME(6)과 같은 마이크로초 단위로 자른 현재 시각입니다. */
    static Instant now(Clock clock) {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
