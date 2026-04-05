package org.musinsa.payments.point.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 포인트 적립 원천 타입.
 *
 * <p>{@link Point} 엔티티의 {@code pointSourceType} 필드에 사용되며,
 * 사용 시 차감 우선순위에도 영향을 준다.
 *
 * <ul>
 *   <li>{@code ACCUMULATION}: 일반 적립 (구매 리워드 등)</li>
 *   <li>{@code MANUAL}: 수기 지급 — 사용 시 최우선 차감</li>
 *   <li>{@code AUTO_RESTORED}: 만료된 적립 건의 사용 취소 시 자동 재발급</li>
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public enum PointSourceType {

    ACCUMULATION("일반 적립"),
    MANUAL("수기 지급"),
    AUTO_RESTORED("만료 후 취소 자동 재발급");

    /** 적립 원천 타입 한글 설명 */
    private final String description;
}
