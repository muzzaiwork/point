package org.musinsa.payments.point.repository;

import jakarta.persistence.LockModeType;
import org.musinsa.payments.point.domain.UserAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 사용자 계정 레포지토리.
 */
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    /**
     * userId로 사용자를 조회한다.
     */
    Optional<UserAccount> findByUserId(String userId);

    /**
     * userId로 사용자를 조회하되 Pessimistic Write Lock을 건다.
     * 동시 적립/사용/취소 요청에 의한 잔액 불일치를 방지하기 위해 사용한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserAccount u WHERE u.userId = :userId")
    Optional<UserAccount> findByUserIdWithLock(@Param("userId") String userId);

    /**
     * 조건 기반 사용자 검색 (Admin 관리 화면용).
     * 모든 조건은 선택적이며, null이면 해당 조건을 무시한다.
     */
    @Query("""
        SELECT u FROM UserAccount u
        WHERE (:userId IS NULL OR u.userId = :userId)
          AND (:name IS NULL OR u.name LIKE %:name%)
    """)
    Page<UserAccount> searchAccounts(
            @Param("userId") String userId,
            @Param("name")   String name,
            Pageable pageable
    );
}
