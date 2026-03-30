package org.musinsa.payments.point.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 포인트 타입 (무료/유료)
 */
@Getter
@RequiredArgsConstructor
public enum PointType {
    FREE("무료"),
    PAID("유료");

    private final String description;
}
