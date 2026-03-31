package org.musinsa.payments.point.repository;

import jakarta.persistence.LockModeType;
import org.musinsa.payments.point.domain.PointKeySequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface PointKeySequenceRepository extends JpaRepository<PointKeySequence, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM PointKeySequence s WHERE s.sequenceDate = :date")
    Optional<PointKeySequence> findBySequenceDateWithLock(@Param("date") LocalDate date);
}
