package org.musinsa.payments.point;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * 포인트 시스템 애플리케이션 진입점.
 *
 * <p>{@code @EnableJpaAuditing}: {@link org.musinsa.payments.point.domain.BaseEntity}의
 * {@code @LastModifiedDate} 등 JPA Auditing 기능을 활성화한다.
 */
@EnableJpaAuditing
@SpringBootApplication
public class PointApplication {

    public static void main(String[] args) {
        SpringApplication.run(PointApplication.class, args);
    }
}
