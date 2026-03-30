package org.musinsa.payments.point.service;

import lombok.RequiredArgsConstructor;
import org.musinsa.payments.point.common.ResultCode;
import org.musinsa.payments.point.domain.PointAccumulation;
import org.musinsa.payments.point.domain.PointUsage;
import org.musinsa.payments.point.domain.PointUsageDetail;
import org.musinsa.payments.point.domain.User;
import org.musinsa.payments.point.exception.BusinessException;
import org.musinsa.payments.point.repository.PointAccumulationRepository;
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

    private final PointAccumulationRepository accumulationRepository;
    private final PointUsageRepository usageRepository;
    private final PointUsageDetailRepository usageDetailRepository;
    private final UserRepository userRepository;

    @Value("${point.config.max-accumulation:100000}")
    private Long maxAccumulationPerTime;

    @Value("${point.config.max-retention:1000000}")
    private Long maxRetentionPerUser;

    @Value("${point.config.default-expiry-days:365}")
    private Integer defaultExpiryDays;

    /**
     * 포인트를 적립한다.
     * @param userId 사용자 ID
     * @param amount 적립 금액
     * @param isManual 수기 지급 여부
     * @param expiryDays 만료일 수 (기본값 365일)
     * @return 생성된 적립 포인트의 고유 키
     */
    @Transactional
    public String accumulate(String userId, Long amount, boolean isManual, Integer expiryDays) {
        // 0. 사용자 조회 (비관적 락 적용하여 동시성 제어)
        User user = userRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        // 1. 1회 적립 가능 포인트 체크 (1포인트 이상 10만포인트 이하)
        if (amount < 1 || amount > maxAccumulationPerTime) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "1회 적립 가능한 포인트 범위를 벗어났습니다.");
        }

        // 2. 만료일 설정 (최소 1일 이상 5년 미만)
        LocalDateTime now = LocalDateTime.now();
        int days = (expiryDays != null) ? expiryDays : defaultExpiryDays;
        if (days < 1 || days >= 365 * 5) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "만료일은 최소 1일 이상, 최대 5년 미만이어야 합니다.");
        }
        LocalDateTime expiryDate = now.plusDays(days);

        // 3. 사용자 엔티티에서 잔액 및 한도 체크 후 적립
        user.addPoint(amount);

        PointAccumulation accumulation = PointAccumulation.builder()
                .userId(userId)
                .pointKey(UUID.randomUUID().toString())
                .amount(amount)
                .remainingAmount(amount)
                .isManual(isManual)
                .accumulationDate(now)
                .expiryDate(expiryDate)
                .isCancelled(false)
                .build();

        accumulationRepository.save(accumulation);
        return accumulation.getPointKey();
    }

    /**
     * 적립을 취소한다.
     * @param pointKey 취소할 적립 건의 고유 키
     */
    @Transactional
    public void cancelAccumulation(String pointKey) {
        PointAccumulation accumulation = accumulationRepository.findByPointKey(pointKey)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "적립 내역을 찾을 수 없습니다."));
        
        // 사용자 잔액 차감을 위해 사용자 조회 (락 획득)
        User user = userRepository.findByUserIdWithLock(accumulation.getUserId())
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        long amountToCancel = accumulation.getRemainingAmount();
        
        // 사용된 금액이 있는 경우 취소 불가 로직은 엔티티 내부에서 체크
        accumulation.cancel();
        
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
        List<PointAccumulation> availablePoints = accumulationRepository.findAvailablePoints(userId, now);
        
        long totalAvailable = availablePoints.stream().mapToLong(PointAccumulation::getRemainingAmount).sum();
        if (totalAvailable < useAmount) {
            // User 테이블의 totalPoint와 PointAccumulation의 합계가 맞지 않는 상황 (데이터 정합성 이슈 대비)
            throw new BusinessException(ResultCode.POINT_SHORTAGE, "사용 가능한 포인트 잔액이 부족합니다.");
        }

        // 3. PointUsage 기록
        PointUsage usage = PointUsage.builder()
                .userId(userId)
                .orderNo(orderNo)
                .pointKey(UUID.randomUUID().toString())
                .totalAmount(useAmount)
                .usageDate(now)
                .build();
        usageRepository.save(usage);

        // 4. 포인트 차감 및 상세 내역 기록 (1원 단위 추적 가능)
        long remainingToUse = useAmount;
        for (PointAccumulation acc : availablePoints) {
            if (remainingToUse <= 0) break;

            long canUseFromThis = Math.min(acc.getRemainingAmount(), remainingToUse);
            acc.use(canUseFromThis);
            remainingToUse -= canUseFromThis;

            PointUsageDetail detail = PointUsageDetail.builder()
                    .pointUsage(usage)
                    .pointAccumulation(acc)
                    .amount(canUseFromThis)
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

        // 1. 사용자 잔액 복구 (한도 체크 포함)
        user.addPoint(cancelAmount);

        List<PointUsageDetail> details = usageDetailRepository.findByPointUsage(usage);
        LocalDateTime now = LocalDateTime.now();

        long remainingToCancel = cancelAmount;
        
        // 사용 내역 상세를 바탕으로 복구
        for (PointUsageDetail detail : details) {
            if (remainingToCancel <= 0) break;

            long amountToRestore = Math.min(detail.getAmount(), remainingToCancel);
            PointAccumulation acc = detail.getPointAccumulation();
            
            if (acc.isExpired(now)) {
                // 사용 취소 시점에 이미 만료된 포인트라면 신규 적립 처리
                // 이미 위에서 user.addPoint(cancelAmount)를 했으므로, 
                // accumulate 내부에서도 user.addPoint를 하면 중복 차감/적립이 발생함.
                // 따라서 만료된 경우 신규 PointAccumulation만 생성해야 함.
                createNewAccumulationForExpiredCancellation(usage.getUserId(), amountToRestore, acc.isManual());
            } else {
                // 만료되지 않은 경우 기존 적립 건의 잔액 복구
                acc.restore(amountToRestore);
            }
            
            remainingToCancel -= amountToRestore;
        }
    }

    /**
     * 사용 취소 시 만료된 포인트에 대해 신규 적립 내역만 생성 (User 잔액은 이미 업데이트됨)
     */
    private void createNewAccumulationForExpiredCancellation(String userId, Long amount, boolean isManual) {
        LocalDateTime now = LocalDateTime.now();
        PointAccumulation accumulation = PointAccumulation.builder()
                .userId(userId)
                .pointKey(UUID.randomUUID().toString())
                .amount(amount)
                .remainingAmount(amount)
                .isManual(isManual)
                .accumulationDate(now)
                .expiryDate(now.plusDays(defaultExpiryDays))
                .isCancelled(false)
                .build();
        accumulationRepository.save(accumulation);
    }
}
