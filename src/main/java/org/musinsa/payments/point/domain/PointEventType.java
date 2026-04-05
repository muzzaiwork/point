package org.musinsa.payments.point.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 포인트 이벤트 타입.
 *
 * <p>포인트와 관련된 모든 활동을 분류한다.
 * {@link PointEvent} 엔티티의 {@code pointEventType} 필드에 사용된다.
 *
 * <ul>
 *   <li>{@code ACCUMULATE}: 일반 적립 (ACCUMULATION, MANUAL 원천)</li>
 *   <li>{@code ACCUMULATE_CANCEL}: 적립 취소</li>
 *   <li>{@code USE}: 포인트 사용</li>
 *   <li>{@code USE_CANCEL}: 사용 취소 (유효한 적립 건 복구)</li>
 *   <li>{@code EXPIRE}: 포인트 만료</li>
 *   <li>{@code EXPIRED_CANCEL_RESTORE}: 만료된 적립 건의 사용 취소 시 신규 적립 처리</li>
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public enum PointEventType {

    ACCUMULATE("적립"),
    ACCUMULATE_CANCEL("적립 취소"),
    USE("사용"),
    USE_CANCEL("사용 취소"),
    EXPIRE("만료"),
    EXPIRED_CANCEL_RESTORE("만료 후 취소 재적립");

    /** 이벤트 타입 한글 설명 */
    private final String description;
}
