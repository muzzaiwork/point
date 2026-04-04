package org.musinsa.payments.point.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.musinsa.payments.point.domain.*;
import org.musinsa.payments.point.repository.*;
import org.musinsa.payments.point.service.PointService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 애플리케이션 시작 시 1년치 더미 데이터를 삽입한다.
 * 날짜별로 하루 10건씩 적립/적립취소/만료/만료후취소/사용/사용취소 복합 케이스를 실제 서비스 로직으로 생성한다.
 */
@Slf4j
@Profile("!test")
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserAccountRepository userAccountRepository;
    private final PointService pointService;
    private final PointRepository pointRepository;
    private final PointEventRepository pointEventRepository;
    private final OrderRepository orderRepository;

    // 시나리오 인덱스 순환용
    private final AtomicInteger scenarioIdx = new AtomicInteger(0);

    // 날짜별로 사용할 유저 목록 (10명)
    private static final String[] USER_IDS = {
        "seed01", "seed02", "seed03", "seed04", "seed05",
        "seed06", "seed07", "seed08", "seed09", "seed10"
    };

    // 복잡한 케이스 전용 유저 목록
    private static final String[] COMPLEX_USER_IDS = {
        "complex01", "complex02", "complex03"
    };

    @Override
    public void run(ApplicationArguments args) {
        if (userAccountRepository.findByUserId("seed01").isPresent()) {
            log.info("[DataInitializer] 이미 데이터가 존재합니다. 초기화를 건너뜁니다.");
            return;
        }

        log.info("[DataInitializer] 더미 데이터 삽입을 시작합니다.");
        long start = System.currentTimeMillis();

        // 유저 생성
        createUsers();

        // 2025-01-01 ~ 2025-12-31 날짜별 순회
        LocalDate startDate = LocalDate.of(2025, 1, 1);
        LocalDate endDate = LocalDate.of(2025, 12, 31);

        // 날짜별 적립 포인트키 추적 (만료/취소 시나리오에서 재사용)
        // key: userId, value: deque of (pointKey, orderNo, date)
        Map<String, Deque<PointRecord>> userPointQueue = new HashMap<>();
        Map<String, Deque<PointRecord>> userOrderQueue = new HashMap<>();
        for (String uid : USER_IDS) {
            userPointQueue.put(uid, new ArrayDeque<>());
            userOrderQueue.put(uid, new ArrayDeque<>());
        }

        int totalEvents = 0;
        int dayCount = 0;

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            dayCount++;
            // 하루 10건: 유저별로 1건씩
            for (int i = 0; i < USER_IDS.length; i++) {
                String userId = USER_IDS[i];
                // 시나리오를 날짜+유저 인덱스 기반으로 결정 (다양하게 분포)
                int scenario = (dayCount + i) % 10;
                int hour = 9 + i; // 09시~18시 분산

                LocalDateTime dateTime = date.atTime(hour, 0, 0);

                try {
                    int events = runScenario(scenario, userId, date, dateTime,
                            userPointQueue.get(userId), userOrderQueue.get(userId));
                    totalEvents += events;
                } catch (Exception e) {
                    // 비즈니스 예외(잔액부족 등)는 무시하고 계속
                } finally {
                    DateTimeContext.clear();
                }
            }
        }

        // 복잡한 케이스 유저 생성 및 시나리오 실행 (3번 반복)
        createComplexUsers();
        LocalDateTime complexBase = LocalDate.of(2026, 1, 1).atTime(10, 0, 0);
        for (int i = 0; i < COMPLEX_USER_IDS.length; i++) {
            try {
                totalEvents += runComplexScenario(COMPLEX_USER_IDS[i], complexBase.plusDays(i));
            } catch (Exception e) {
                log.warn("[DataInitializer] 복잡한 케이스 실행 중 오류: {}", e.getMessage());
            } finally {
                DateTimeContext.clear();
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("[DataInitializer] 시드 데이터 생성 완료. 총 이벤트: {}건, 소요시간: {}ms", totalEvents, elapsed);
    }

    /**
     * DateTimeContext를 step 순서에 따라 +분 단위로 증가시켜 설정한다.
     */
    private void setTime(LocalDateTime base, int stepMinutes) {
        DateTimeContext.set(base.plusMinutes(stepMinutes));
    }

    /**
     * 시나리오별 실행. 반환값은 생성된 이벤트 수.
     */
    private int runScenario(int scenario, String userId, LocalDate date, LocalDateTime dateTime,
                             Deque<PointRecord> pointQueue, Deque<PointRecord> orderQueue) {
        String dateSuffix = date.toString().replace("-", "");

        switch (scenario) {
            case 0 -> {
                // 단순 적립
                setTime(dateTime, 0);
                String orderNo = "ACC-" + userId + "-" + dateSuffix;
                String pk = pointService.accumulate(userId, 5000L, PointSourceType.ACCUMULATION, PointType.FREE, 365, orderNo);
                pointQueue.addLast(new PointRecord(pk, orderNo, date));
                return 1;
            }
            case 1 -> {
                // 적립 후 즉시 적립취소
                setTime(dateTime, 0);
                String orderNo = "ACC-" + userId + "-" + dateSuffix + "-C";
                String pk = pointService.accumulate(userId, 3000L, PointSourceType.ACCUMULATION, PointType.FREE, 180, orderNo);
                setTime(dateTime, 10);
                pointService.cancelAccumulation(pk);
                return 2;
            }
            case 2 -> {
                // 적립 후 사용
                setTime(dateTime, 0);
                String accOrderNo = "ACC-" + userId + "-" + dateSuffix + "-U";
                String pk = pointService.accumulate(userId, 8000L, PointSourceType.ACCUMULATION, PointType.FREE, 365, accOrderNo);
                pointQueue.addLast(new PointRecord(pk, accOrderNo, date));

                setTime(dateTime, 20);
                String useOrderNo = "USE-" + userId + "-" + dateSuffix;
                pointService.use(userId, useOrderNo, 4000L);
                orderQueue.addLast(new PointRecord(null, useOrderNo, date));
                return 2;
            }
            case 3 -> {
                // 사용 후 전액 취소
                if (!orderQueue.isEmpty()) {
                    PointRecord rec = orderQueue.pollFirst();
                    setTime(dateTime, 0);
                    try {
                        Order order = orderRepository.findByOrderNo(rec.orderNo).orElse(null);
                        if (order != null) {
                            long remaining = order.getOrderedPoint() - order.getCanceledPoint();
                            if (remaining > 0) {
                                pointService.cancelUsage(rec.orderNo, remaining);
                                return 1;
                            }
                        }
                    } catch (Exception ignored) {}
                }
                // 큐가 비어있으면 단순 적립으로 대체
                setTime(dateTime, 0);
                String orderNo = "ACC-" + userId + "-" + dateSuffix + "-F";
                String pk = pointService.accumulate(userId, 2000L, PointSourceType.ACCUMULATION, PointType.FREE, 90, orderNo);
                pointQueue.addLast(new PointRecord(pk, orderNo, date));
                return 1;
            }
            case 4 -> {
                // 사용 후 부분 취소
                setTime(dateTime, 0);
                String accOrderNo = "ACC-" + userId + "-" + dateSuffix + "-P";
                String pk = pointService.accumulate(userId, 10000L, PointSourceType.ACCUMULATION, PointType.FREE, 365, accOrderNo);
                pointQueue.addLast(new PointRecord(pk, accOrderNo, date));

                setTime(dateTime, 20);
                String useOrderNo = "USE-" + userId + "-" + dateSuffix + "-P";
                pointService.use(userId, useOrderNo, 6000L);
                setTime(dateTime, 40);
                // 부분 취소 (3000원만)
                pointService.cancelUsage(useOrderNo, 3000L);
                return 3;
            }
            case 5 -> {
                // 만료 강제처리 후 취소 → AUTO_RESTORED
                setTime(dateTime, 0);
                String accOrderNo = "ACC-" + userId + "-" + dateSuffix + "-E";
                String pk = pointService.accumulate(userId, 7000L, PointSourceType.ACCUMULATION, PointType.FREE, 30, accOrderNo);

                setTime(dateTime, 20);
                String useOrderNo = "USE-" + userId + "-" + dateSuffix + "-E";
                pointService.use(userId, useOrderNo, 5000L);

                // 강제 만료 처리 (+40분)
                setTime(dateTime, 40);
                forceExpire(pk, dateTime.plusMinutes(40));

                // 사용 취소 → AUTO_RESTORED 발생 (+60분)
                setTime(dateTime, 60);
                try {
                    pointService.cancelUsage(useOrderNo, 5000L);
                } catch (Exception ignored) {}
                return 4;
            }
            case 6 -> {
                // 여러 건 적립 후 전액 사용
                setTime(dateTime, 0);
                String pk1 = pointService.accumulate(userId, 2000L, PointSourceType.ACCUMULATION, PointType.FREE, 365,
                        "ACC-" + userId + "-" + dateSuffix + "-M1");
                setTime(dateTime, 10);
                String pk2 = pointService.accumulate(userId, 3000L, PointSourceType.ACCUMULATION, PointType.FREE, 365,
                        "ACC-" + userId + "-" + dateSuffix + "-M2");
                pointQueue.addLast(new PointRecord(pk1, null, date));
                pointQueue.addLast(new PointRecord(pk2, null, date));

                setTime(dateTime, 30);
                String useOrderNo = "USE-" + userId + "-" + dateSuffix + "-M";
                pointService.use(userId, useOrderNo, 5000L);
                orderQueue.addLast(new PointRecord(null, useOrderNo, date));
                return 3;
            }
            case 7 -> {
                // 수기 지급 + 사용
                setTime(dateTime, 0);
                String pk = pointService.accumulate(userId, 4000L, PointSourceType.MANUAL, PointType.FREE, null, null);
                pointQueue.addLast(new PointRecord(pk, null, date));

                setTime(dateTime, 30);
                String useOrderNo = "USE-" + userId + "-" + dateSuffix + "-MN";
                try {
                    pointService.use(userId, useOrderNo, 2000L);
                    orderQueue.addLast(new PointRecord(null, useOrderNo, date));
                } catch (Exception ignored) {}
                return 2;
            }
            case 8 -> {
                // 복합: 적립 → 사용 → 사용취소 → 재사용 → 만료 → 취소(AUTO_RESTORED)
                setTime(dateTime, 0);
                String accOrderNo = "ACC-" + userId + "-" + dateSuffix + "-CX";
                String pk = pointService.accumulate(userId, 12000L, PointSourceType.ACCUMULATION, PointType.FREE, 30, accOrderNo);
                pointQueue.addLast(new PointRecord(pk, accOrderNo, date));

                // 1차 사용
                setTime(dateTime, 10);
                String useOrderNo1 = "USE-" + userId + "-" + dateSuffix + "-CX1";
                pointService.use(userId, useOrderNo1, 5000L);

                // 1차 사용취소
                setTime(dateTime, 20);
                pointService.cancelUsage(useOrderNo1, 5000L);

                // 재사용
                setTime(dateTime, 30);
                String useOrderNo2 = "USE-" + userId + "-" + dateSuffix + "-CX2";
                try {
                    pointService.use(userId, useOrderNo2, 7000L);
                } catch (Exception ignored) {}

                // 강제 만료 (잔액 5000 남은 상태)
                setTime(dateTime, 50);
                forceExpire(pk, dateTime.plusMinutes(50));

                // 만료 후 취소 → AUTO_RESTORED
                setTime(dateTime, 70);
                try {
                    pointService.cancelUsage(useOrderNo2, 7000L);
                } catch (Exception ignored) {}
                return 6;
            }
            case 9 -> {
                // 복합: 적립 → 사용 → 부분취소 → 재사용 → 만료 → 취소(AUTO_RESTORED)
                setTime(dateTime, 0);
                String accOrderNo = "ACC-" + userId + "-" + dateSuffix + "-ER";
                String pk = pointService.accumulate(userId, 9000L, PointSourceType.ACCUMULATION, PointType.FREE, 30, accOrderNo);

                // 1차 사용
                setTime(dateTime, 15);
                String useOrderNo1 = "USE-" + userId + "-" + dateSuffix + "-ER1";
                pointService.use(userId, useOrderNo1, 6000L);

                // 부분 취소 (3000만)
                setTime(dateTime, 25);
                pointService.cancelUsage(useOrderNo1, 3000L);

                // 재사용 (취소로 복원된 포인트 재사용)
                setTime(dateTime, 35);
                String useOrderNo2 = "USE-" + userId + "-" + dateSuffix + "-ER2";
                try {
                    pointService.use(userId, useOrderNo2, 4000L);
                } catch (Exception ignored) {}

                // 강제 만료
                setTime(dateTime, 50);
                forceExpire(pk, dateTime.plusMinutes(50));

                // 만료 후 취소 → AUTO_RESTORED
                setTime(dateTime, 65);
                try {
                    pointService.cancelUsage(useOrderNo2, 4000L);
                } catch (Exception ignored) {}
                return 6;
            }
            default -> {
                return 0;
            }
        }
    }

    /**
     * 가장 복잡한 케이스:
     * [1회~3회 반복]
     *   - 적립 (만료 30일)
     *   - 사용 2건
     *   - 사용취소 2건 (정상 취소)
     *   - 재사용 1건
     *   - 강제 만료
     *   - 사용취소 (만료 후 취소 → AUTO_RESTORED 재지급 #1)
     *   - 재지급 #1에서 사용
     *   - 재지급 #1 강제 만료
     *   - 사용취소 (만료 후 취소 → AUTO_RESTORED 재지급 #2)
     *   → 재지급 #2가 다음 라운드의 시작 포인트
     */
    private int runComplexScenario(String userId, LocalDateTime base) {
        int totalEvents = 0;
        int minuteOffset = 0;
        String prevRestoredPointKey = null;

        for (int round = 1; round <= 3; round++) {
            String roundSuffix = "-R" + round;

            // 적립 (만료 30일) 또는 이전 라운드 재지급 포인트 사용
            setTime(base, minuteOffset);
            String accOrderNo = "ACC-" + userId + roundSuffix;
            String pk;
            if (prevRestoredPointKey == null) {
                pk = pointService.accumulate(userId, 20000L, PointSourceType.ACCUMULATION, PointType.FREE, 30, accOrderNo);
                totalEvents += 1;
            } else {
                pk = prevRestoredPointKey;
            }
            minuteOffset += 10;

            // 사용 1차
            setTime(base, minuteOffset);
            String useOrder1 = "USE-" + userId + roundSuffix + "-U1";
            pointService.use(userId, useOrder1, 3000L);
            minuteOffset += 10;
            totalEvents += 1;

            // 사용 2차
            setTime(base, minuteOffset);
            String useOrder2 = "USE-" + userId + roundSuffix + "-U2";
            pointService.use(userId, useOrder2, 4000L);
            minuteOffset += 10;
            totalEvents += 1;

            // 사용취소 1차 (useOrder1 정상 취소)
            setTime(base, minuteOffset);
            pointService.cancelUsage(useOrder1, 3000L);
            minuteOffset += 10;
            totalEvents += 1;

            // 사용취소 2차 (useOrder2 정상 취소)
            setTime(base, minuteOffset);
            pointService.cancelUsage(useOrder2, 4000L);
            minuteOffset += 10;
            totalEvents += 1;

            // 재사용 3차
            setTime(base, minuteOffset);
            String useOrder3 = "USE-" + userId + roundSuffix + "-U3";
            pointService.use(userId, useOrder3, 5000L);
            minuteOffset += 10;
            totalEvents += 1;

            // 강제 만료 (잔액이 남은 상태)
            setTime(base, minuteOffset);
            forceExpire(pk, base.plusMinutes(minuteOffset));
            minuteOffset += 10;
            totalEvents += 1;

            // 사용취소 3차 (useOrder3) → 만료 후 취소 → AUTO_RESTORED 재지급 #1
            setTime(base, minuteOffset);
            String restored1Pk = null;
            try {
                pointService.cancelUsage(useOrder3, 5000L);
                totalEvents += 1;
                var restoredPoints = pointRepository.findByUserIdOrderByIdDesc(userId);
                if (!restoredPoints.isEmpty()) {
                    restored1Pk = restoredPoints.get(0).getPointKey();
                }
            } catch (Exception ignored) {}
            minuteOffset += 10;

            if (restored1Pk == null) {
                prevRestoredPointKey = null;
                continue;
            }

            // 재지급 #1에서 사용 4차
            setTime(base, minuteOffset);
            String useOrder4 = "USE-" + userId + roundSuffix + "-U4";
            try {
                pointService.use(userId, useOrder4, 6000L);
                totalEvents += 1;
            } catch (Exception ignored) {}
            minuteOffset += 10;

            // 재지급 #1 강제 만료
            setTime(base, minuteOffset);
            forceExpire(restored1Pk, base.plusMinutes(minuteOffset));
            minuteOffset += 10;
            totalEvents += 1;

            // 사용취소 4차 (useOrder4) → 만료 후 취소 → AUTO_RESTORED 재지급 #2
            setTime(base, minuteOffset);
            String restoredPk = null;
            try {
                pointService.cancelUsage(useOrder4, 6000L);
                totalEvents += 1;
                var restoredPoints = pointRepository.findByUserIdOrderByIdDesc(userId);
                if (!restoredPoints.isEmpty()) {
                    restoredPk = restoredPoints.get(0).getPointKey();
                }
            } catch (Exception ignored) {}
            minuteOffset += 10;

            prevRestoredPointKey = restoredPk;
        }

        return totalEvents;
    }

    /**
     * 포인트를 강제 만료 처리한다.
     */
    @Transactional
    public void forceExpire(String pointKey, LocalDateTime baseDateTime) {
        pointRepository.findByPointKey(pointKey).ifPresent(p -> {
            p.setExpiryDateTime(baseDateTime.minusDays(1));
            long remaining = p.getRemainingPoint();
            p.expire();
            pointRepository.save(p);
            if (remaining > 0) {
                // DateTimeContext는 호출부(DataInitializer)에서 이미 설정됨
                PointEvent expireEvent = PointEvent.builder()
                        .point(p)
                        .pointEventType(PointEventType.EXPIRE)
                        .amount(remaining)
                        .build();
                pointEventRepository.save(expireEvent);
            }
        });
    }

    private void createComplexUsers() {
        String[] names = {"복잡케이스1", "복잡케이스2", "복잡케이스3"};
        for (int i = 0; i < COMPLEX_USER_IDS.length; i++) {
            userAccountRepository.save(UserAccount.builder()
                    .userId(COMPLEX_USER_IDS[i])
                    .name(names[i])
                    .maxAccumulationPoint(500000L)
                    .maxRetentionPoint(5000000L)
                    .build());
        }
        log.info("[DataInitializer] 복잡케이스 유저 {}명 생성 완료.", COMPLEX_USER_IDS.length);
    }

    private void createUsers() {
        String[] names = {"김민준", "이서연", "박지훈", "최수아", "정도윤",
                          "강하은", "조민서", "윤지우", "장서준", "임나은"};
        for (int i = 0; i < USER_IDS.length; i++) {
            userAccountRepository.save(UserAccount.builder()
                    .userId(USER_IDS[i])
                    .name(names[i])
                    .maxAccumulationPoint(500000L)
                    .maxRetentionPoint(5000000L)
                    .build());
        }
        log.info("[DataInitializer] {}명 유저 생성 완료.", USER_IDS.length);
    }

    private record PointRecord(String pointKey, String orderNo, LocalDate date) {}
}
