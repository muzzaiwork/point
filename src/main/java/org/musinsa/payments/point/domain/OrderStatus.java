package org.musinsa.payments.point.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 주문 진행 상태 (진행중, 확정)
 */
@Getter
@RequiredArgsConstructor
public enum OrderStatus {
    IN_PROGRESS("진행중"),
    CONFIRMED("확정");

    private final String description;
}
