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
    private final PointEventRepository pointEventRepository;
    private final UserAccountRepository userAccountRepository;
    private final PointKeySequenceRepository sequenceRepository;

    /**
     * 포인트를 적립한다.
     * @param userId 사용자 ID
     * @param amount 적립 금액
     * @param pointSourceType 포인트 적립 원천 타입 (ACCUMULATION, MANUAL, AUTO_RESTORED)
     * @param type 포인트 타입 (FREE, PAID)
     * @param expiryDays 만료일 수 (미입력 시 2999-12-31)
     * @param orderNo 적립 근거 주문 번호
     * @return 생성된 적립 포인트의 고유 키
     */
    @Transactional
    public String accumulate(String userId, Long amount, PointSourceType pointSourceType, PointType type, Integer expiryDays, String orderNo) {
        // 0. 사용자 조회 (비관적 락 적용하여 동시성 제어)
        UserAccount user = userAccountRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        // 1. 만료일 설정 (미입력 시 2999-12-31)
        LocalDateTime expiryDate = resolveExpiryDate(expiryDays);

        // 2. 사용자 엔티티에서 잔액 및 한도 체크 후 적립
        user.accumulatePoint(amount, type);

        return doAccumulate(userId, amount, pointSourceType, type, expiryDate, orderNo, null, null);
    }

    /**
     * 포인트 적립의 핵심 로직 (Point 저장 + PointEvent 저장)
     * 외부 적립(accumulate)과 만료 후 취소 재적립 모두 이 메서드를 통해 처리한다.
     */
    private String doAccumulate(String userId, Long amount, PointSourceType pointSourceType, PointType type,
                                 LocalDateTime expiryDate, String orderNo, Long originPointId, Long rootPointId) {
        Point point = Point.builder()
                .userId(userId)
                .pointKey(generatePointKey())
                .orderNo(orderNo)
                .accumulatedPoint(amount)
                .remainingPoint(amount)
                .type(type)
                .pointSourceType(pointSourceType)
                .originPointId(originPointId)
                .rootPointId(rootPointId)
                .expiryDateTime(expiryDate)
                .expiryDate(expiryDate.toLocalDate())
                .isCancelled(false)
                .build();

        pointRepository.save(point);

        PointEvent pointEvent = PointEvent.builder()
                .point(point)
                .pointEventType(PointEventType.ACCUMULATE)
                .amount(amount)
                .build();

        pointEventRepository.save(pointEvent);

        return point.getPointKey();
    }

    private LocalDateTime resolveExpiryDate(Integer expiryDays) {
        if (expiryDays != null) {
            return LocalDateTime.now().plusDays(expiryDays);
        }
        return LocalDateTime.of(2999, 12, 31, 23, 59, 59);
    }

    /**
     * 적립을 취소한다.
     * 이미 사용된 금액이 있는 경우 취소할 수 없다. (부분 취소 불가)
     *
     * @param pointKey 취소할 적립 건의 고유 키
     */
    @Transactional
    public void cancelAccumulation(String pointKey) {
        // 0. 적립 건 조회
        Point point = pointRepository.findByPointKey(pointKey)
                .orElseThrow(() -> new BusinessException(ResultCode.POINT_NOT_FOUND));

        // 1. 사용자 조회 (비관적 락 적용하여 동시성 제어)
        UserAccount user = userAccountRepository.findByUserIdWithLock(point.getUserId())
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        // 2. 취소 처리 (이미 취소 여부 및 사용 여부 검증은 엔티티 내부에서 수행)
        //    - isCancelled == true → ALREADY_CANCELLED 예외
        //    - remainingPoint < accumulatedPoint → ALREADY_USED 예외
        long amountToCancel = point.getRemainingPoint();
        point.cancel();

        // 3. 적립 취소 이벤트 기록
        PointEvent detail = PointEvent.builder()
                .point(point)
                .pointEventType(PointEventType.ACCUMULATE_CANCEL)
                .amount(amountToCancel)
                .build();
        pointEventRepository.save(detail);

        // 4. 사용자 잔액 차감 (적립 철회)
        user.cancelAccumulation(amountToCancel, point.getType());
    }

    /**
     * 포인트를 사용한다.
     * 수기 지급(MANUAL) 포인트를 우선 사용하고, 이후 만료일 임박 순으로 차감한다.
     *
     * @param userId    사용자 ID
     * @param orderNo   주문 번호 (중복 불가)
     * @param useAmount 사용 금액
     * @return 생성된 주문 번호
     */
    @Transactional
    public String use(String userId, String orderNo, Long useAmount) {
        // 0. 사용자 조회 (비관적 락 적용하여 동시성 제어)
        UserAccount user = userAccountRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        // 1. 전체 잔액 사전 체크 (루프 진입 전 빠른 실패)
        if (user.getRemainingPoint() < useAmount) {
            throw new BusinessException(ResultCode.POINT_SHORTAGE, "보유 포인트가 부족합니다.");
        }

        // 2. 사용 가능한 적립 내역 조회 (MANUAL 우선, 만료일 임박 순)
        List<Point> availablePoints = pointRepository.findAvailablePoints(userId);

        // 3. Order 생성 (주문 번호는 외부에서 전달받아 식별자로 사용)
        Order order = Order.builder()
                .userId(userId)
                .orderNo(orderNo)
                .orderedPoint(useAmount)
                .canceledPoint(0L)
                .type(OrderType.PURCHASE)
                .status(OrderStatus.IN_PROGRESS)
                .build();
        orderRepository.save(order);

        // 4. 적립 건별 포인트 차감 및 PointEvent(USE) 기록 (1원 단위 추적)
        long remainingToUse = useAmount;
        for (Point acc : availablePoints) {
            if (remainingToUse <= 0) break;

            long canUseFromThis = Math.min(acc.getRemainingPoint(), remainingToUse);
            acc.use(canUseFromThis);
            user.usePoint(canUseFromThis, acc.getType());
            remainingToUse -= canUseFromThis;

            PointEvent detail = PointEvent.builder()
                    .order(order)
                    .point(acc)
                    .pointEventType(PointEventType.USE)
                    .amount(canUseFromThis)
                    .build();
            pointEventRepository.save(detail);
        }

        return order.getOrderNo();
    }

    /**
     * 포인트 사용을 취소한다.
     * 부분 취소와 전체 취소를 모두 지원하며, 만료된 포인트는 신규 적립으로 처리한다.
     *
     * @param orderNo      취소할 주문 번호
     * @param cancelAmount 취소할 금액
     */
    @Transactional
    public void cancelUsage(String orderNo, Long cancelAmount) {
        // 0. 주문 조회
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(ResultCode.ORDER_NOT_FOUND));

        // 1. 사용자 조회 및 비관적 락 획득 (동시 취소 요청 방지)
        UserAccount user = userAccountRepository.findByUserIdWithLock(order.getUserId())
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        // 2. 주문 취소 금액 누적 및 취소 상태 갱신 (orderedPoint는 불변, canceledPoint 누적)
        //    취소 가능 잔여 금액(orderedPoint - canceledPoint) 초과 시 예외 발생
        order.cancel(cancelAmount);

        // 3. 취소 이력(OrderCancel) 저장
        OrderCancel orderCancel = OrderCancel.builder()
                .order(order)
                .cancelAmount(cancelAmount)
                .build();
        orderCancelRepository.save(orderCancel);

        // 4. 사용 이벤트(USE) 역순 조회 후 건별 취소 처리
        //    가장 최근에 차감된 적립 건부터 역순으로 복구 (LIFO)
        List<PointEvent> details = pointEventRepository.findByOrderAndPointEventTypeOrderByIdDesc(order, PointEventType.USE);
        long remainingToCancel = cancelAmount;

        for (PointEvent useDetail : details) {
            if (remainingToCancel <= 0) break;

            // 이미 취소된 금액을 제외한 실제 취소 가능 금액 계산
            long alreadyCanceledAmount = getAlreadyCanceledAmount(useDetail);
            long canCancelFromThis = Math.min(useDetail.getAmount() - alreadyCanceledAmount, remainingToCancel);
            if (canCancelFromThis <= 0) continue;

            Point acc = useDetail.getPoint();
            if (acc.isExpired()) {
                // 4-1. 만료된 적립 건: 원본 복구 불가 → AUTO_RESTORED 타입으로 신규 적립
                //      만료일은 2999-12-31로 설정, originPointId/rootPointId 상속
                //      accumulatePoint() 대신 cancelUsage()로 잔액 복구 (1회 적립 한도 우회)
                user.cancelUsage(canCancelFromThis, acc.getType());
                doAccumulate(order.getUserId(), canCancelFromThis, PointSourceType.AUTO_RESTORED, acc.getType(),
                        LocalDateTime.of(2999, 12, 31, 23, 59, 59), null, acc.getId(), acc.getRootPointId());
            } else {
                // 4-2. 유효한 적립 건: 기존 포인트 잔액 복구
                acc.restore(canCancelFromThis);
                pointRepository.save(acc);
                user.cancelUsage(canCancelFromThis, acc.getType());
            }

            // 5. USE_CANCEL 이벤트 기록
            PointEvent cancelDetail = PointEvent.builder()
                    .order(order)
                    .point(acc)
                    .pointEventType(PointEventType.USE_CANCEL)
                    .amount(canCancelFromThis)
                    .orderCancel(orderCancel)
                    .build();
            pointEventRepository.save(cancelDetail);

            remainingToCancel -= canCancelFromThis;
        }
    }

    private long getAlreadyCanceledAmount(PointEvent useDetail) {
        return pointEventRepository.findByOrderAndPointAndPointEventType(useDetail.getOrder(), useDetail.getPoint(), PointEventType.USE_CANCEL)
                .stream()
                .mapToLong(PointEvent::getAmount)
                .sum();
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
