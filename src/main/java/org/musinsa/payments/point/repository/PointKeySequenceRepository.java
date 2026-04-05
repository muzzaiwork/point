package org.musinsa.payments.point.repository;

import jakarta.persistence.LockModeType;
import org.musinsa.payments.point.domain.PointKeySequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 포인트 키 시퀀스 레포지토리.
 */
public interface PointKeySequenceRepository extends JpaRepository<PointKeySequence, Long> {

    /**
     * 특정 날짜의 시퀀스를 Pessimistic Write Lock을 걸고 조회한다.
     * 동시 요청 시 중복 키 생성을 방지하기 위해 Lock이 필수적이다.
     *
     * @param date 조회할 날짜
     * @return 해당 날짜의 시퀀스 (없으면 empty)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM PointKeySequence s WHERE s.sequenceDate = :date")
    Optional<PointKeySequence> findBySequenceDateWithLock(@Param("date") LocalDate date);
}
