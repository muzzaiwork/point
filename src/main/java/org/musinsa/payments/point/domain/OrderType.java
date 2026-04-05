package org.musinsa.payments.point.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 주문 타입.
 *
 * <p>주문 생성 시 {@code PURCHASE}로 시작하며,
 * 취소 요청에 따라 {@code PARTIAL_CANCEL} 또는 {@code TOTAL_CANCEL}로 변경된다.
 *
 * <ul>
 *   <li>{@code PURCHASE}: 일반 구매 (취소 전)</li>
 *   <li>{@code PARTIAL_CANCEL}: 부분 취소 (canceledPoint &lt; orderedPoint)</li>
 *   <li>{@code TOTAL_CANCEL}: 전체 취소 (canceledPoint == orderedPoint)</li>
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public enum OrderType {

    PURCHASE("구매"),
    PARTIAL_CANCEL("부분 취소"),
    TOTAL_CANCEL("전체 취소");

    /** 주문 타입 한글 설명 */
    private final String description;
}
