package org.musinsa.payments.point.service;

import lombok.RequiredArgsConstructor;
import org.musinsa.payments.point.common.ResultCode;
import org.musinsa.payments.point.domain.Point;
import org.musinsa.payments.point.domain.PointType;
import org.musinsa.payments.point.domain.PointUsage;
import org.musinsa.payments.point.domain.PointUsageDetail;
import org.musinsa.payments.point.domain.User;
import org.musinsa.payments.point.exception.BusinessException;
import org.musinsa.payments.point.repository.PointRepository;
import org.musinsa.payments.point.repository.PointUsageDetailRepository;
import org.musinsa.payments.point.repository.PointUsageRepository;
import org.musinsa.payments.point.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 포인트 비즈니스 로직을 처리하는 서비스 클래스
 */
@Service
@RequiredArgsConstructor
public class PointService {

    private final PointRepository pointRepository;
    private final PointUsageRepository usageRepository;
    private final PointUsageDetailRepository usageDetailRepository;
    private final UserRepository userRepository;

    @Value("${point.config.default-expiry-days:365}")
    private Integer defaultExpiryDays;

    /**
     * 포인트를 적립한다.
     * @param userId 사용자 ID
     * @param amount 적립 금액
     * @param isManual 수기 지급 여부
     * @param typeStr 포인트 타입 (FREE, PAID)
     * @param expiryDays 만료일 수 (미입력 시 2999-12-31)
     * @return 생성된 적립 포인트의 고유 키
     */
    @Transactional
    public String accumulate(String userId, Long amount, boolean isManual, String typeStr, Integer expiryDays) {
        // 0. 사용자 조회 (비관적 락 적용하여 동시성 제어)
        User user = userRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        // 1. 만료일 설정 (미입력 시 2999-12-31)
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiryDate;
        if (expiryDays != null) {
            expiryDate = now.plusDays(expiryDays);
        } else {
            expiryDate = LocalDateTime.of(2999, 12, 31, 23, 59, 59);
        }

        // 2. 포인트 타입 설정
        PointType type = PointType.FREE;
        if (typeStr != null) {
            try {
                type = PointType.valueOf(typeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "잘못된 포인트 타입입니다.");
            }
        }

        // 3. 사용자 엔티티에서 잔액 및 한도 체크 후 적립
        user.addPoint(amount);

        Point point = Point.builder()
                .userId(userId)
                .pointKey(UUID.randomUUID().toString())
                .amount(amount)
                .remainingAmount(amount)
                .isManual(isManual)
                .type(type)
                .accumulationDate(now)
                .expiryDate(expiryDate)
                .isCancelled(false)
                .build();

        pointRepository.save(point);
        return point.getPointKey();
    }

    /**
     * 적립을 취소한다.
     * @param pointKey 취소할 적립 건의 고유 키
     */
    @Transactional
    public void cancelAccumulation(String pointKey) {
        Point point = pointRepository.findByPointKey(pointKey)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "적립 내역을 찾을 수 없습니다."));
        
        // 사용자 잔액 차감을 위해 사용자 조회 (락 획득)
        User user = userRepository.findByUserIdWithLock(point.getUserId())
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        long amountToCancel = point.getRemainingAmount();
        
        // 사용된 금액이 있는 경우 취소 불가 로직은 엔티티 내부에서 체크
        point.cancel();
        
        // 사용자 잔액 차감
        user.usePoint(amountToCancel);
    }

    /**
     * 포인트를 사용한다.
     * @param userId 사용자 ID
     * @param orderNo 주문 번호
     * @param useAmount 사용 금액
     * @return 생성된 사용 내역의 고유 키
     */
    @Transactional
    public String use(String userId, String orderNo, Long useAmount) {
        // 0. 사용자 조회 및 락 획득
        User user = userRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        // 1. 사용자 잔액 체크 및 차감
        user.usePoint(useAmount);

        LocalDateTime now = LocalDateTime.now();
        
        // 2. 사용 가능한 상세 적립 내역 조회 (수기 지급 우선, 만료일 임박 순)
        List<Point> availablePoints = pointRepository.findAvailablePoints(userId, now);
        
        // 3. PointUsage 기록
        PointUsage usage = PointUsage.builder()
                .userId(userId)
                .orderNo(orderNo)
                .pointKey(UUID.randomUUID().toString())
                .totalAmount(useAmount)
                .cancelledAmount(0L)
                .usageDate(now)
                .build();
        usageRepository.save(usage);

        // 4. 포인트 차감 및 상세 내역 기록 (1원 단위 추적 가능)
        long remainingToUse = useAmount;
        for (Point acc : availablePoints) {
            if (remainingToUse <= 0) break;

            long canUseFromThis = Math.min(acc.getRemainingAmount(), remainingToUse);
            acc.use(canUseFromThis);
            remainingToUse -= canUseFromThis;

            PointUsageDetail detail = PointUsageDetail.builder()
                    .pointUsage(usage)
                    .point(acc)
                    .amount(canUseFromThis)
                    .cancelledAmount(0L)
                    .usageDate(now)
                    .build();
            usageDetailRepository.save(detail);
        }

        return usage.getPointKey();
    }

    /**
     * 사용을 취소한다.
     * @param usagePointKey 사용 내역의 고유 키
     * @param cancelAmount 취소할 금액
     */
    @Transactional
    public void cancelUsage(String usagePointKey, Long cancelAmount) {
        PointUsage usage = usageRepository.findByPointKey(usagePointKey)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "사용 내역을 찾을 수 없습니다."));

        if (cancelAmount > usage.getTotalAmount()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "취소 금액이 사용 금액을 초과할 수 없습니다.");
        }

        // 0. 사용자 조회 및 락 획득
        User user = userRepository.findByUserIdWithLock(usage.getUserId())
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        // 1. 사용 내역 업데이트 (금액 차감 및 취소 금액 누적)
        usage.cancel(cancelAmount);

        // 2. 사용자 잔액 복구 (한도 체크 포함)
        user.addPoint(cancelAmount);

        List<PointUsageDetail> details = usageDetailRepository.findByPointUsage(usage);
        LocalDateTime now = LocalDateTime.now();

        long remainingToCancel = cancelAmount;
        
        // 사용 내역 상세를 바탕으로 복구
        for (PointUsageDetail detail : details) {
            if (remainingToCancel <= 0) break;

            long restorableInThisDetail = detail.getAmount() - detail.getCancelledAmount();
            if (restorableInThisDetail <= 0) continue;

            long amountToRestore = Math.min(restorableInThisDetail, remainingToCancel);
            Point acc = detail.getPoint();
            
            if (acc.isExpired(now)) {
                createNewAccumulationForExpiredCancellation(usage.getUserId(), amountToRestore, acc.isManual(), acc.getType());
            } else {
                acc.restore(amountToRestore);
            }
            
            detail.addCancelledAmount(amountToRestore);
            remainingToCancel -= amountToRestore;
        }
    }

    /**
     * 사용 취소 시 만료된 포인트에 대해 신규 적립 내역만 생성 (User 잔액은 이미 업데이트됨)
     */
    private void createNewAccumulationForExpiredCancellation(String userId, Long amount, boolean isManual, PointType type) {
        LocalDateTime now = LocalDateTime.now();
        // 만료된 포인트 사용 취소 시 기본적으로 2999-12-31까지로 재적립 (요구사항에 맞춰 정책 결정 가능)
        LocalDateTime expiryDate = LocalDateTime.of(2999, 12, 31, 23, 59, 59);
        
        Point point = Point.builder()
                .userId(userId)
                .pointKey(UUID.randomUUID().toString())
                .amount(amount)
                .remainingAmount(amount)
                .isManual(isManual)
                .type(type)
                .accumulationDate(now)
                .expiryDate(expiryDate)
                .isCancelled(false)
                .build();
        pointRepository.save(point);
    }
}
