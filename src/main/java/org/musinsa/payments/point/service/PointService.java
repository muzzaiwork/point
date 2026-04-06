package org.musinsa.payments.point.service;

import lombok.RequiredArgsConstructor;
import org.musinsa.payments.point.common.ResultCode;
import org.musinsa.payments.point.domain.*;
import org.musinsa.payments.point.exception.BusinessException;
import org.musinsa.payments.point.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 포인트 비즈니스 로직을 처리하는 서비스 클래스.
 *
 * <p>주요 기능:
 * <ul>
 *   <li>포인트 적립 / 적립 취소</li>
 *   <li>포인트 사용 / 사용 취소</li>
 * </ul>
 *
 * <p>동시성 제어:
 * 모든 쓰기 작업은 {@code UserAccount}에 Pessimistic Lock을 걸어
 * 동시 요청에 의한 잔액 불일치를 방지합니다.
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

    // =========================================================================
    // 적립
    // =========================================================================

    /**
     * 포인트를 적립한다.
     *
     * <p>처리 순서:
     * <ol>
     *   <li>사용자 조회 (Pessimistic Lock)</li>
     *   <li>만료일 계산</li>
     *   <li>1회 적립 한도 및 최대 보유 한도 검증 후 잔액 증가</li>
     *   <li>Point 엔티티 생성 및 PointEvent(ACCUMULATE) 기록</li>
     * </ol>
     *
     * @param userId          사용자 ID
     * @param amount          적립 금액
     * @param pointSourceType 적립 원천 타입 (ACCUMULATION, MANUAL)
     * @param type            포인트 타입 (FREE, PAID)
     * @param expiryDays      만료일 수 (null 이면 2999-12-31)
     * @param orderNo         적립 근거 주문 번호 (선택)
     * @return 생성된 적립 건의 고유 키 (pointKey)
     */
    @Transactional
    public String accumulate(String userId, Long amount, PointSourceType pointSourceType,
                             PointType type, Integer expiryDays, String orderNo) {
        // 1. 사용자 조회 — Pessimistic Lock으로 동시 적립 요청에 의한 한도 초과 방지
        UserAccount user = userAccountRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        // 2. 만료일 계산 (expiryDays 미입력 시 2999-12-31)
        LocalDateTime expiryDate = resolveExpiryDate(expiryDays);

        // 3. 1회 적립 한도 및 최대 보유 한도 검증 후 사용자 잔액 증가
        user.accumulatePoint(amount, type);

        // 4. Point 저장 + PointEvent(ACCUMULATE) 기록
        return doAccumulate(userId, amount, pointSourceType, type, expiryDate, orderNo,
                null, null, null);
    }

    // =========================================================================
    // 적립 취소
    // =========================================================================

    /**
     * 포인트 적립을 취소한다.
     *
     * <p>취소 불가 조건 (엔티티 내부에서 검증):
     * <ul>
     *   <li>{@code isCancelled == true} → 이미 취소된 건 ({@code ALREADY_CANCELLED})</li>
     *   <li>{@code remainingPoint != accumulatedPoint} → 일부라도 사용된 건 ({@code ALREADY_USED})</li>
     * </ul>
     *
     * @param pointKey 취소할 적립 건의 고유 키
     */
    @Transactional
    public void cancelAccumulation(String pointKey) {
        // 1. 적립 건 조회
        Point point = pointRepository.findByPointKey(pointKey)
                .orElseThrow(() -> new BusinessException(ResultCode.POINT_NOT_FOUND));

        // 2. 사용자 조회 — Pessimistic Lock
        UserAccount user = userAccountRepository.findByUserIdWithLock(point.getUserId())
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        // 3. 취소 처리 — 이미 취소 여부 및 사용 여부 검증은 Point.cancel() 내부에서 수행
        long amountToCancel = point.getRemainingPoint();
        point.cancel();

        // 4. PointEvent(ACCUMULATE_CANCEL) 기록
        PointEvent cancelEvent = PointEvent.builder()
                .point(point)
                .pointEventType(PointEventType.ACCUMULATE_CANCEL)
                .amount(amountToCancel)
                .build();
        pointEventRepository.save(cancelEvent);

        // 5. 사용자 잔액 차감 (적립 철회)
        user.cancelAccumulation(amountToCancel, point.getType());
    }

    // =========================================================================
    // 사용
    // =========================================================================

    /**
     * 포인트를 사용한다.
     *
     * <p>차감 우선순위:
     * <ol>
     *   <li>수기 지급(MANUAL) 포인트 우선</li>
     *   <li>만료일 임박 순 (expiryDateTime ASC)</li>
     * </ol>
     *
     * <p>처리 순서:
     * <ol>
     *   <li>사용자 조회 (Pessimistic Lock) 및 잔액 사전 검증</li>
     *   <li>사용 가능한 적립 건 목록 조회</li>
     *   <li>Order 생성</li>
     *   <li>적립 건별 잔액 차감 + PointEvent(USE) 기록 (1원 단위 추적)</li>
     * </ol>
     *
     * @param userId    사용자 ID
     * @param orderNo   주문 번호 (중복 불가)
     * @param useAmount 사용 금액
     * @return 생성된 주문 번호 (orderNo)
     */
    @Transactional
    public String use(String userId, String orderNo, Long useAmount) {
        // 1. 사용자 조회 — Pessimistic Lock
        UserAccount user = userAccountRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        // 2. 전체 잔액 사전 검증 (루프 진입 전 빠른 실패)
        if (user.getRemainingPoint() < useAmount) {
            throw new BusinessException(ResultCode.POINT_SHORTAGE, "보유 포인트가 부족합니다.");
        }

        // 3. 사용 가능한 적립 건 조회 (MANUAL 우선, 만료일 임박 순)
        List<Point> availablePoints = pointRepository.findAvailablePoints(userId);

        // 4. Order 생성 (주문 번호는 외부에서 전달받아 식별자로 사용)
        Order order = Order.builder()
                .userId(userId)
                .orderNo(orderNo)
                .orderedPoint(useAmount)
                .canceledPoint(0L)
                .type(OrderType.PURCHASE)
                .status(OrderStatus.IN_PROGRESS)
                .build();
        orderRepository.save(order);

        // 5. 적립 건별 포인트 차감 및 PointEvent(USE) 기록
        long remainingToUse = useAmount;
        for (Point point : availablePoints) {
            if (remainingToUse <= 0) break;

            long canUseFromThis = Math.min(point.getRemainingPoint(), remainingToUse);
            point.use(canUseFromThis);
            pointRepository.save(point);
            user.usePoint(canUseFromThis, point.getType());
            remainingToUse -= canUseFromThis;

            PointEvent useEvent = PointEvent.builder()
                    .order(order)
                    .point(point)
                    .pointEventType(PointEventType.USE)
                    .amount(canUseFromThis)
                    .build();
            pointEventRepository.save(useEvent);
        }

        return order.getOrderNo();
    }

    // =========================================================================
    // 사용 취소
    // =========================================================================

    /**
     * 포인트 사용을 취소한다. 부분 취소와 전체 취소를 모두 지원한다.
     *
     * <p>복구 방식:
     * <ul>
     *   <li>유효한 적립 건: 기존 Point의 remainingPoint 복구 + PointEvent(USE_CANCEL) 기록</li>
     *   <li>만료된 적립 건: AUTO_RESTORED 타입의 신규 Point 생성 + PointEvent(EXPIRED_CANCEL_RESTORE) 기록
     *       (원본 Point는 변경하지 않으며, originPointKey/rootPointKey를 상속)</li>
     * </ul>
     *
     * <p>복구 순서: USE 이벤트를 역순(LIFO)으로 조회하여 가장 최근 차감 건부터 복구.
     *
     * @param orderNo      취소할 주문 번호
     * @param cancelAmount 취소 금액 (부분 취소 가능)
     */
    @Transactional
    public void cancelUsage(String orderNo, Long cancelAmount) {
        // 1. 주문 조회
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(ResultCode.ORDER_NOT_FOUND));

        // 2. 사용자 조회 — Pessimistic Lock (동시 취소 요청 방지)
        UserAccount user = userAccountRepository.findByUserIdWithLock(order.getUserId())
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        // 3. 주문 취소 금액 누적 및 상태 갱신
        //    - orderedPoint는 불변, canceledPoint 누적
        //    - 취소 가능 잔여 금액(orderedPoint - canceledPoint) 초과 시 예외 발생
        //    - 전액 취소 시 TOTAL_CANCEL, 부분 취소 시 PARTIAL_CANCEL
        order.cancel(cancelAmount);

        // 4. 취소 이력(OrderCancel) 저장
        OrderCancel orderCancel = OrderCancel.builder()
                .order(order)
                .cancelAmount(cancelAmount)
                .build();
        orderCancelRepository.save(orderCancel);

        // 5. USE 이벤트를 역순(LIFO)으로 조회하여 건별 복구
        List<PointEvent> useEvents = pointEventRepository
                .findByOrderAndPointEventTypeOrderByIdDesc(order, PointEventType.USE);
        long remainingToCancel = cancelAmount;

        for (PointEvent useEvent : useEvents) {
            if (remainingToCancel <= 0) break;

            // 이미 취소된 금액을 제외한 실제 취소 가능 금액 계산
            long alreadyCanceled = getAlreadyCanceledAmount(useEvent);
            long canCancelFromThis = Math.min(useEvent.getAmount() - alreadyCanceled, remainingToCancel);
            if (canCancelFromThis <= 0) continue;

            Point point = useEvent.getPoint();

            if (point.isExpired()) {
                // 5-1. 만료된 적립 건: 원본 복구 불가 → AUTO_RESTORED 타입 신규 적립
                //      - 만료일: 2999-12-31 (사실상 무기한)
                //      - originPointKey / rootPointKey 는 원본에서 상속
                //      - 1회 적립 한도 우회를 위해 accumulatePoint() 대신 cancelUsage() 사용
                //      - USE_CANCEL 이벤트는 기록하지 않고 EXPIRED_CANCEL_RESTORE 이벤트만 기록
                user.cancelUsage(canCancelFromThis, point.getType());
                doAccumulate(order.getUserId(), canCancelFromThis,
                        PointSourceType.AUTO_RESTORED, point.getType(),
                        LocalDateTime.of(2999, 12, 31, 23, 59, 59),
                        null, point.getPointKey(), point.getRootPointKey(), orderCancel);
            } else {
                // 5-2. 유효한 적립 건: 기존 Point 잔액 복구 + PointEvent(USE_CANCEL) 기록
                point.restore(canCancelFromThis);
                pointRepository.save(point);
                user.cancelUsage(canCancelFromThis, point.getType());

                PointEvent cancelEvent = PointEvent.builder()
                        .order(order)
                        .point(point)
                        .pointEventType(PointEventType.USE_CANCEL)
                        .amount(canCancelFromThis)
                        .orderCancel(orderCancel)
                        .build();
                pointEventRepository.save(cancelEvent);
            }

            remainingToCancel -= canCancelFromThis;
        }
    }

    // =========================================================================
    // private 헬퍼 메서드
    // =========================================================================

    /**
     * Point 저장 및 PointEvent 기록의 공통 로직.
     *
     * <p>일반 적립({@code ACCUMULATION}, {@code MANUAL})과
     * 만료 후 취소 재적립({@code AUTO_RESTORED}) 모두 이 메서드를 통해 처리한다.
     *
     * <ul>
     *   <li>{@code rootPointKey}가 null이면 자기 자신의 pointKey로 초기화 (최초 적립)</li>
     *   <li>{@code pointSourceType == AUTO_RESTORED}이면 PointEvent 타입을 {@code EXPIRED_CANCEL_RESTORE}로 기록</li>
     * </ul>
     *
     * @param userId          사용자 ID
     * @param amount          적립 금액
     * @param pointSourceType 적립 원천 타입
     * @param type            포인트 타입 (FREE, PAID)
     * @param expiryDate      만료 일시
     * @param orderNo         적립 근거 주문 번호 (없으면 null)
     * @param originPointKey  원본 적립 건의 pointKey (만료 재적립 시 설정, 일반 적립 시 null)
     * @param rootPointKey    최상위 적립 건의 pointKey (null이면 자기 자신으로 초기화)
     * @param orderCancel     연관 취소 이력 (없으면 null)
     * @return 생성된 Point의 pointKey
     */
    private String doAccumulate(String userId, Long amount, PointSourceType pointSourceType,
                                PointType type, LocalDateTime expiryDate, String orderNo,
                                String originPointKey, String rootPointKey, OrderCancel orderCancel) {
        String newPointKey = generatePointKey();

        Point point = Point.builder()
                .userId(userId)
                .pointKey(newPointKey)
                .orderNo(orderNo)
                .accumulatedPoint(amount)
                .remainingPoint(amount)
                .type(type)
                .pointSourceType(pointSourceType)
                .originPointKey(originPointKey)
                .rootPointKey(rootPointKey != null ? rootPointKey : newPointKey)
                .expiryDateTime(expiryDate)
                .expiryDate(expiryDate.toLocalDate())
                .isCancelled(false)
                .build();
        pointRepository.save(point);

        // AUTO_RESTORED 타입은 EXPIRED_CANCEL_RESTORE 이벤트로 기록, 그 외는 ACCUMULATE
        PointEventType eventType = (pointSourceType == PointSourceType.AUTO_RESTORED)
                ? PointEventType.EXPIRED_CANCEL_RESTORE
                : PointEventType.ACCUMULATE;

        PointEvent accumulateEvent = PointEvent.builder()
                .point(point)
                .pointEventType(eventType)
                .amount(amount)
                .orderCancel(orderCancel)
                .build();
        pointEventRepository.save(accumulateEvent);

        return point.getPointKey();
    }

    /**
     * 만료일 계산.
     * expiryDays가 null이면 사실상 무기한(2999-12-31)으로 설정한다.
     *
     * @param expiryDays 만료일 수 (null 허용)
     * @return 계산된 만료 일시
     */
    private LocalDateTime resolveExpiryDate(Integer expiryDays) {
        if (expiryDays != null) {
            LocalDateTime base = org.musinsa.payments.point.config.DateTimeContext.get();
            LocalDateTime from = (base != null) ? base : LocalDateTime.now();
            return from.plusDays(expiryDays);
        }
        return LocalDateTime.of(2999, 12, 31, 23, 59, 59);
    }

    /**
     * 특정 USE 이벤트에 대해 이미 취소된 금액을 계산한다.
     *
     * <p>취소 금액은 두 가지 경로로 추적한다:
     * <ul>
     *   <li>유효한 포인트: 동일 주문·포인트에 연결된 {@code USE_CANCEL} 이벤트 합산</li>
     *   <li>만료된 포인트: 동일 주문에 연결된 {@code EXPIRED_CANCEL_RESTORE} 이벤트 중
     *       originPointKey가 해당 포인트의 pointKey인 건 합산</li>
     * </ul>
     *
     * @param useEvent 취소 대상 USE 이벤트
     * @return 이미 취소된 금액 합계
     */
    private long getAlreadyCanceledAmount(PointEvent useEvent) {
        // 유효한 포인트 취소 금액: USE_CANCEL 이벤트로 추적
        long useCancelAmount = pointEventRepository
                .findByOrderAndPointAndPointEventType(
                        useEvent.getOrder(), useEvent.getPoint(), PointEventType.USE_CANCEL)
                .stream()
                .mapToLong(PointEvent::getAmount)
                .sum();

        // 만료된 포인트 취소 금액: EXPIRED_CANCEL_RESTORE 이벤트로 추적
        long expiredCancelAmount = pointEventRepository
                .findByOrderAndPointEventTypeAndOriginPointKey(
                        useEvent.getOrder(), PointEventType.EXPIRED_CANCEL_RESTORE,
                        useEvent.getPoint().getPointKey())
                .stream()
                .mapToLong(PointEvent::getAmount)
                .sum();

        return useCancelAmount + expiredCancelAmount;
    }

    /**
     * 날짜 기반 고유 포인트 키를 생성한다.
     *
     * <p>형식: {@code yyyyMMdd + 6자리 시퀀스} (예: {@code 20260401000001})
     * <p>{@code PointKeySequence} 테이블에 날짜별 시퀀스를 관리하며,
     * 해당 날짜의 첫 번째 요청이면 새 시퀀스 레코드를 생성한다.
     *
     * @return 생성된 고유 포인트 키
     */
    @Transactional
    protected String generatePointKey() {
        LocalDate today = LocalDate.now();

        PointKeySequence sequence = sequenceRepository.findBySequenceDateWithLock(today)
                .orElseGet(() -> {
                    PointKeySequence newSeq = PointKeySequence.builder()
                            .sequenceDate(today)
                            .lastSequence(0L)
                            .build();
                    return sequenceRepository.saveAndFlush(newSeq);
                });

        sequence.increment();
        sequenceRepository.save(sequence);

        String dateStr = today.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return String.format("%s%06d", dateStr, sequence.getLastSequence());
    }
}
