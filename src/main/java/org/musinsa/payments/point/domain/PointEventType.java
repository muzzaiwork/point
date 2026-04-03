package org.musinsa.payments.point.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 포인트 상세 내역 타입
 */
@Getter
@RequiredArgsConstructor
public enum PointEventType {
    ACCUMULATE("적립"),
    ACCUMULATE_CANCEL("적립취소"),
    USE("사용"),
    USE_CANCEL("사용취소"),
    EXPIRE("만료");

    private final String description;
}
