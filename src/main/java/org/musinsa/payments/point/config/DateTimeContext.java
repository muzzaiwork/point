package org.musinsa.payments.point.config;

import java.time.LocalDateTime;

/**
 * 더미 데이터 생성 시 엔티티의 regDateTime을 특정 날짜로 고정하기 위한 ThreadLocal 컨텍스트.
 */
public class DateTimeContext {

    private static final ThreadLocal<LocalDateTime> CURRENT_DATE_TIME = new ThreadLocal<>();

    public static void set(LocalDateTime dateTime) {
        CURRENT_DATE_TIME.set(dateTime);
    }

    public static LocalDateTime get() {
        return CURRENT_DATE_TIME.get();
    }

    public static void clear() {
        CURRENT_DATE_TIME.remove();
    }
}
