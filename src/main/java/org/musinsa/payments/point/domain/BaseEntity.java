package org.musinsa.payments.point.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.musinsa.payments.point.config.DateTimeContext;

@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Column(nullable = false, updatable = false)
    private LocalDateTime regDateTime;

    @Column(nullable = false, updatable = false)
    private LocalDate regDate;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updDateTime;

    @Column(nullable = false)
    private LocalDate updDate;

    // JPA Auditing은 LocalDateTime만 자동으로 채워주므로, LocalDate는 @PrePersist, @PreUpdate에서 처리
    @jakarta.persistence.PrePersist
    public void prePersist() {
        LocalDateTime ctx = DateTimeContext.get();
        LocalDateTime now = (ctx != null) ? ctx : LocalDateTime.now();
        if (regDateTime == null) regDateTime = now;
        if (updDateTime == null) updDateTime = now;
        regDate = now.toLocalDate();
        updDate = now.toLocalDate();
    }

    @jakarta.persistence.PreUpdate
    public void preUpdate() {
        updDateTime = LocalDateTime.now();
        updDate = updDateTime.toLocalDate();
    }
}
