package org.musinsa.payments.point.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 포인트 타입.
 *
 * <p>무료({@code FREE})와 유료({@code PAID})로 구분하며,
 * {@link UserAccount}의 잔액 집계 시 타입별로 분리하여 관리한다.
 */
@Getter
@RequiredArgsConstructor
public enum PointType {

    FREE("무료"),
    PAID("유료");

    /** 포인트 타입 한글 설명 */
    private final String description;
}
