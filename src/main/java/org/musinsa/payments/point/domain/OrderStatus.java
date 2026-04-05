package org.musinsa.payments.point.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 주문 진행 상태.
 *
 * <ul>
 *   <li>{@code IN_PROGRESS}: 주문 진행 중 (사용 취소 가능)</li>
 *   <li>{@code CONFIRMED}: 주문 확정 완료</li>
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public enum OrderStatus {

    IN_PROGRESS("진행 중"),
    CONFIRMED("확정");

    /** 주문 상태 한글 설명 */
    private final String description;
}
