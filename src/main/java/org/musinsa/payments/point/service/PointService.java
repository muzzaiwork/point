package org.musinsa.payments.point.service;

import lombok.RequiredArgsConstructor;
import org.musinsa.payments.point.common.ResultCode;
import org.musinsa.payments.point.domain.*;
import org.musinsa.payments.point.exception.BusinessException;
import org.musinsa.payments.point.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 포인트 비즈니스 로직을 처리하는 서비스 클래스
 */
@Service
@RequiredArgsConstructor
public class PointService {

    private final PointRepository pointRepository;
    private final OrderRepository orderRepository;
    private final OrderCancelRepository orderCancelRepository;
    private final PointUsageDetailRepository usageDetailRepository;
    private final UserAccountRepository userRepository;
    private final PointKeySequenceRepository sequenceRepository;

    @Value("${point.accumulation.max-limit}")
    private long maxSystemAccumulationLimit;

    /**
     * 포인트를 적립한다.
     * @param userId 사용자 ID
     * @param amount 적립 금액
     * @param isManual 수기 지급 여부
     * @param type 포인트 타입 (FREE, PAID)
     * @param expiryDays 만료일 수 (미입력 시 2999-12-31)
     * @param orderNo 적립 근거 주문 번호
     * @return 생성된 적립 포인트의 고유 키
     */
    @Transactional
    public String accumulate(String userId, Long amount, boolean isManual, PointType type, Integer expiryDays, String orderNo) {
        // 0. 사용자 조회 (비관적 락 적용하여 동시성 제어)
        UserAccount user = userRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        // 1. 시스템 공통 적립 상한 검증
        if (amount > maxSystemAccumulationLimit) {
            throw new BusinessException(ResultCode.ACCUMULATION_LIMIT_EXCEEDED, "시스템 적립 상한(" + maxSystemAccumulationLimit + ")을 초과하였습니다.");
        }

        // 2. 만료일 설정 (미입력 시 2999-12-31)
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiryDate;
        if (expiryDays != null) {
            expiryDate = now.plusDays(expiryDays);
        } else {
            expiryDate = LocalDateTime.of(2999, 12, 31, 23, 59, 59);
        }

        // 2. 사용자 엔티티에서 잔액 및 한도 체크 후 적립
        user.addPoint(amount, type);

        Point point = Point.builder()
                .userId(userId)
                .pointKey(generatePointKey())
                .orderNo(orderNo)
                .accumulatedPoint(amount)
                .remainingPoint(amount)
                .isManual(isManual)
                .type(type)
                .expiryDateTime(expiryDate)
                .expiryDate(expiryDate.toLocalDate())
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
                .orElseThrow(() -> new BusinessException(ResultCode.POINT_NOT_FOUND));
        
        // 사용자 잔액 차감을 위해 사용자 조회 (락 획득)
        UserAccount user = userRepository.findByUserIdWithLock(point.getUserId())
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        long amountToCancel = point.getRemainingPoint();
        
        // 사용된 금액이 있는 경우 취소 불가 로직은 엔티티 내부에서 체크
        point.cancel();
        
        // 사용자 잔액 차감
        user.cancelAccumulation(amountToCancel, point.getType());
    }

    /**
     * 포인트를 사용한다.
     * @param userId 사용자 ID
     * @param orderNo 주문 번호
     * @param useAmount 사용 금액
     * @return 주문 번호
     */
    @Transactional
    public String use(String userId, String orderNo, Long useAmount) {
        // 0. 사용자 조회 및 락 획득
        UserAccount user = userRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        // 1. 사용자 잔액 체크 및 차감
        // 전체 잔액 체크만 수행 (상세 차감은 하위 루프에서 유/무료 구분하여 수행)
        if (user.getRemainingPoint() < useAmount) {
            throw new BusinessException(ResultCode.POINT_SHORTAGE, "보유 포인트가 부족합니다.");
        }

        // 2. 사용 가능한 상세 적립 내역 조회 (수기 지급 우선, 만료일 임박 순)
        List<Point> availablePoints = pointRepository.findAvailablePoints(userId);
        
        // 3. Order 기록
        Order order = Order.builder()
                .userId(userId)
                .orderNo(orderNo)
                .totalAmount(useAmount)
                .cancelledAmount(0L)
                .type(OrderType.PURCHASE)
                .build();
        orderRepository.save(order);

        // 4. 포인트 차감 및 상세 내역 기록 (1원 단위 추적 가능)
        long remainingToUse = useAmount;
        for (Point acc : availablePoints) {
            if (remainingToUse <= 0) break;

            long canUseFromThis = Math.min(acc.getRemainingPoint(), remainingToUse);
            acc.use(canUseFromThis);
            user.usePoint(canUseFromThis, acc.getType());
            remainingToUse -= canUseFromThis;

            PointUsageDetail detail = PointUsageDetail.builder()
                    .order(order)
                    .point(acc)
                    .amount(canUseFromThis)
                    .cancelledAmount(0L)
                    .build();
            usageDetailRepository.save(detail);
        }

        return order.getOrderNo();
    }

    /**
     * 사용을 취소한다.
     * @param orderNo 주문 번호
     * @param cancelAmount 취소할 금액
     */
    @Transactional
    public void cancelUsage(String orderNo, Long cancelAmount) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(ResultCode.ORDER_NOT_FOUND));

        if (cancelAmount > order.getTotalAmount()) {
            throw new BusinessException(ResultCode.CANCEL_AMOUNT_EXCEEDED);
        }

        // 0. 사용자 조회 및 락 획득
        UserAccount user = userRepository.findByUserIdWithLock(order.getUserId())
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        // 1. 주문 내역 업데이트 (취소 금액 누적)
        order.cancel(cancelAmount);

        // 2. 취소 이력 저장
        OrderCancel orderCancel = OrderCancel.builder()
                .order(order)
                .cancelAmount(cancelAmount)
                .build();
        orderCancelRepository.save(orderCancel);

        // 3. 사용자 잔액 복구 (한도 체크 포함)
        // user.addPoint(cancelAmount)는 더 이상 사용하지 않음. 
        // 하위 루프에서 각 적립 타입별로 cancelUsage 호출하여 정밀하게 복구함.

        // 4. 포인트 적립 건별 복구 또는 신규 적립 (만료된 경우)
        // 사용의 역순(최근에 사용된 순서)으로 복구 진행
        List<PointUsageDetail> details = usageDetailRepository.findByOrderOrderByIdDesc(order);
        long remainingToCancel = cancelAmount;

        for (PointUsageDetail detail : details) {
            if (remainingToCancel <= 0) break;

            long canCancelFromThis = Math.min(detail.getAmount() - detail.getCancelledAmount(), remainingToCancel);
            if (canCancelFromThis <= 0) continue;

            Point acc = detail.getPoint();
            if (acc.isExpired()) {
                // 만료된 경우 신규 적립 처리 (2999-12-31까지)
                createNewAccumulationForExpiredCancellation(order.getUserId(), canCancelFromThis, acc.isManual(), acc.getType());
                // 만료된 경우에도 UserAccount 입장에선 사용 취소(복구) 처리됨
                user.cancelUsage(canCancelFromThis, acc.getType());
            } else {
                // 만료되지 않은 경우 기존 적립 건 잔액 복구
                acc.restore(canCancelFromThis);
                pointRepository.save(acc);
                user.cancelUsage(canCancelFromThis, acc.getType());
            }

            // 상세 내역에 취소 정보 기록
            detail.addCancelledAmount(canCancelFromThis);
            detail.setOrderCancel(orderCancel);
            usageDetailRepository.save(detail);
            
            remainingToCancel -= canCancelFromThis;
        }
    }

    /**
     * 사용 취소 시 만료된 포인트에 대해 신규 적립 내역만 생성 (UserAccount 잔액은 이미 업데이트됨)
     */
    private void createNewAccumulationForExpiredCancellation(String userId, Long amount, boolean isManual, PointType type) {
        LocalDateTime now = LocalDateTime.now();
        // 만료된 포인트 사용 취소 시 기본적으로 2999-12-31까지로 재적립 (요구사항에 맞춰 정책 결정 가능)
        LocalDateTime expiryDate = LocalDateTime.of(2999, 12, 31, 23, 59, 59);
        
        Point point = Point.builder()
                .userId(userId)
                .pointKey(generatePointKey())
                .accumulatedPoint(amount)
                .remainingPoint(amount)
                .isManual(isManual)
                .type(type)
                .expiryDateTime(expiryDate)
                .expiryDate(expiryDate.toLocalDate())
                .isCancelled(false)
                .build();
        pointRepository.save(point);
    }

    /**
     * YYYYMMDD + sequence 형태의 고유 키 생성
     * @return 생성된 고유 키 (예: 20260331000001)
     */
    @Transactional
    protected String generatePointKey() {
        LocalDate today = LocalDate.now();
        PointKeySequence sequence = sequenceRepository.findBySequenceDateWithLock(today)
                .orElseGet(() -> {
                    PointKeySequence newSequence = PointKeySequence.builder()
                            .sequenceDate(today)
                            .lastSequence(0L)
                            .build();
                    return sequenceRepository.saveAndFlush(newSequence);
                });

        sequence.increment();
        sequenceRepository.save(sequence);

        String dateStr = today.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return String.format("%s%06d", dateStr, sequence.getLastSequence());
    }
}
