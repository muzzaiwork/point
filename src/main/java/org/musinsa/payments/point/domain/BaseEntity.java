package org.musinsa.payments.point.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.musinsa.payments.point.config.DateTimeContext;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 모든 엔티티의 공통 감사(Auditing) 필드를 제공하는 추상 클래스.
 *
 * <p>등록 일시({@code regDateTime})와 수정 일시({@code updDateTime})를 자동으로 관리한다.
 * JPA Auditing은 {@code LocalDateTime}만 자동으로 채워주므로,
 * {@code LocalDate} 필드는 {@code @PrePersist} / {@code @PreUpdate}에서 직접 처리한다.
 *
 * <p>테스트 환경에서는 {@link DateTimeContext}를 통해 현재 시간을 주입할 수 있다.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    /** 등록 일시 (최초 저장 시 설정, 이후 불변) */
    @Column(nullable = false, updatable = false)
    private LocalDateTime regDateTime;

    /** 등록 날짜 (날짜 기반 조회/집계용) */
    @Column(nullable = false, updatable = false)
    private LocalDate regDate;

    /** 최종 수정 일시 */
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updDateTime;

    /** 최종 수정 날짜 */
    @Column(nullable = false)
    private LocalDate updDate;

    /**
     * 최초 저장 시 등록/수정 일시를 초기화한다.
     * {@link DateTimeContext}에 값이 있으면 해당 값을 사용하고, 없으면 현재 시간을 사용한다.
     */
    @jakarta.persistence.PrePersist
    public void prePersist() {
        LocalDateTime ctx = DateTimeContext.get();
        LocalDateTime now = (ctx != null) ? ctx : LocalDateTime.now();

        if (regDateTime == null) regDateTime = now;
        if (updDateTime == null) updDateTime = now;
        regDate = now.toLocalDate();
        updDate = now.toLocalDate();
    }

    /**
     * 엔티티 수정 시 수정 일시를 갱신한다.
     */
    @jakarta.persistence.PreUpdate
    public void preUpdate() {
        updDateTime = LocalDateTime.now();
        updDate     = updDateTime.toLocalDate();
    }
}
