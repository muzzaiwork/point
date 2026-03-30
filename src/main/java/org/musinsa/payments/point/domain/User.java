package org.musinsa.payments.point.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * 사용자 엔티티
 * 개인별 포인트 보유 한도 등을 관리한다.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String userId;

    @Column(nullable = false)
    private String name;

    /**
     * 개인별 최대 보유 가능 포인트 제한
     */
    @Column(nullable = false)
    private Long maxRetentionPoint;

    /**
     * 보유 한도 변경
     * @param maxRetentionPoint 새로운 보유 한도
     */
    public void updateMaxRetentionPoint(Long maxRetentionPoint) {
        this.maxRetentionPoint = maxRetentionPoint;
    }
}
