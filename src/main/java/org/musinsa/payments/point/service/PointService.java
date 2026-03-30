package org.musinsa.payments.point.service;

import lombok.RequiredArgsConstructor;
import org.musinsa.payments.point.domain.PointAccumulation;
import org.musinsa.payments.point.domain.PointUsage;
import org.musinsa.payments.point.domain.PointUsageDetail;
import org.musinsa.payments.point.repository.PointAccumulationRepository;
import org.musinsa.payments.point.repository.PointUsageDetailRepository;
import org.musinsa.payments.point.repository.PointUsageRepository;
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
        // 1. 1회 적립 가능 포인트 체크 (1포인트 이상 10만포인트 이하)
        if (amount < 1 || amount > maxAccumulationPerTime) {
            throw new IllegalArgumentException("1회 적립 가능한 포인트 범위를 벗어났습니다.");
        }

        // 2. 개인별 보유 가능 포인트 체크 (최대 보유 금액 제한)
        LocalDateTime now = LocalDateTime.now();
        Long currentTotal = accumulationRepository.getValidTotalRemainingAmount(userId, now);
        if (currentTotal == null) currentTotal = 0L;
        
        if (currentTotal + amount > maxRetentionPerUser) {
            throw new IllegalStateException("개인별 최대 보유 가능 포인트를 초과할 수 없습니다.");
        }

        // 3. 만료일 설정 (최소 1일 이상 5년 미만)
        int days = (expiryDays != null) ? expiryDays : defaultExpiryDays;
        if (days < 1 || days >= 365 * 5) {
            throw new IllegalArgumentException("만료일은 최소 1일 이상, 최대 5년 미만이어야 합니다.");
        }
        LocalDateTime expiryDate = now.plusDays(days);

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
                .orElseThrow(() -> new IllegalArgumentException("적립 내역을 찾을 수 없습니다."));
        
        // 사용된 금액이 있는 경우 취소 불가 로직은 엔티티 내부에서 체크
        accumulation.cancel();
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
        LocalDateTime now = LocalDateTime.now();
        
        // 1. 사용 가능한 포인트 조회 (수기 지급 우선, 만료일 임박 순)
        List<PointAccumulation> availablePoints = accumulationRepository.findAvailablePoints(userId, now);
        
        long totalAvailable = availablePoints.stream().mapToLong(PointAccumulation::getRemainingAmount).sum();
        if (totalAvailable < useAmount) {
            throw new IllegalArgumentException("사용 가능한 포인트 잔액이 부족합니다.");
        }

        // 2. PointUsage 기록
        PointUsage usage = PointUsage.builder()
                .userId(userId)
                .orderNo(orderNo)
                .pointKey(UUID.randomUUID().toString())
                .totalAmount(useAmount)
                .usageDate(now)
                .build();
        usageRepository.save(usage);

        // 3. 포인트 차감 및 상세 내역 기록 (1원 단위 추적 가능)
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
                .orElseThrow(() -> new IllegalArgumentException("사용 내역을 찾을 수 없습니다."));

        if (cancelAmount > usage.getTotalAmount()) {
            throw new IllegalArgumentException("취소 금액이 사용 금액을 초과할 수 없습니다.");
        }

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
                accumulate(usage.getUserId(), amountToRestore, acc.isManual(), defaultExpiryDays);
            } else {
                // 만료되지 않은 경우 기존 적립 건의 잔액 복구
                acc.restore(amountToRestore);
            }
            
            remainingToCancel -= amountToRestore;
        }
    }
}
