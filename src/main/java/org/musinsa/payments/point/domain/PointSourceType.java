package org.musinsa.payments.point.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 포인트 적립 원천 타입
 */
@Getter
@RequiredArgsConstructor
public enum PointSourceType {
    ACCUMULATION("적립"),
    MANUAL("수기지급"),
    AUTO_RESTORED("만료후 취소로 인한 자동 지급");

    private final String description;
}
