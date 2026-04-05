package org.musinsa.payments.point.config;

import java.time.LocalDateTime;

/**
 * 현재 시간을 스레드 로컬로 관리하는 유틸리티 클래스.
 *
 * <p>테스트 또는 더미 데이터 생성 시 특정 시점의 시간을 주입하기 위해 사용한다.
 * {@link org.musinsa.payments.point.domain.BaseEntity}의 {@code @PrePersist}에서
 * 이 값을 참조하여 등록 일시를 설정한다.
 *
 * <p>사용 후 반드시 {@link #clear()}를 호출하여 스레드 로컬 값을 제거해야 한다.
 * (메모리 누수 방지)
 *
 * <p>사용 예:
 * <pre>
 *   DateTimeContext.set(LocalDateTime.of(2026, 4, 1, 10, 0, 0));
 *   // ... 엔티티 저장 ...
 *   DateTimeContext.clear();
 * </pre>
 */
public class DateTimeContext {

    private static final ThreadLocal<LocalDateTime> CONTEXT = new ThreadLocal<>();

    /** 현재 스레드에 기준 시간을 설정한다. */
    public static void set(LocalDateTime dateTime) {
        CONTEXT.set(dateTime);
    }

    /** 현재 스레드에 설정된 기준 시간을 반환한다. 설정되지 않은 경우 null을 반환한다. */
    public static LocalDateTime get() {
        return CONTEXT.get();
    }

    /** 현재 스레드의 기준 시간을 제거한다. 사용 후 반드시 호출해야 한다. */
    public static void clear() {
        CONTEXT.remove();
    }
}
