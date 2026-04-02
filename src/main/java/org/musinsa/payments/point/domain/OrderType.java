package org.musinsa.payments.point.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 주문 타입 (구매, 부분 취소, 전체 취소)
 */
@Getter
@RequiredArgsConstructor
public enum OrderType {
    PURCHASE("구매"),
    PARTIAL_CANCEL("부분 취소"),
    TOTAL_CANCEL("전체 취소");

    private final String description;
}
