package com.ganzithon.maeum.concern;

/** 저장된 시각과 현재 시각으로 계산하는 고민 상태입니다. DB에는 저장하지 않습니다. */
enum ConcernStatus {
    MATURING,
    MATURED,
    OPENED,
    SAVED
}
