package org.musinsa.payments.point.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.musinsa.payments.point.domain.*;
import org.musinsa.payments.point.repository.*;
import org.musinsa.payments.point.service.PointService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 애플리케이션 시작 시 다양한 케이스의 더미 데이터를 삽입한다.
 * 이미 데이터가 존재하면 삽입을 건너뛴다.
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

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userAccountRepository.findByUserId("user1").isPresent()) {
            log.info("[DataInitializer] 이미 데이터가 존재합니다. 초기화를 건너뜁니다.");
            return;
        }

        log.info("[DataInitializer] 더미 데이터 삽입을 시작합니다.");

        // ── 사용자 생성 ──────────────────────────────────────────────
        // user1: 일반 사용자 (적립/사용/취소 복합 케이스)
        userAccountRepository.save(UserAccount.builder()
                .userId("user1").name("홍길동")
                .maxAccumulationPoint(100000L).maxRetentionPoint(1000000L)
                .build());

        // user2: 수기 지급 + 만료 케이스
        userAccountRepository.save(UserAccount.builder()
                .userId("user2").name("김철수")
                .maxAccumulationPoint(100000L).maxRetentionPoint(1000000L)
                .build());

        // user3: 부분 취소 + 만료 후 재적립 케이스
        userAccountRepository.save(UserAccount.builder()
                .userId("user3").name("이영희")
                .maxAccumulationPoint(100000L).maxRetentionPoint(1000000L)
                .build());

        // user4: 적립 취소 케이스
        userAccountRepository.save(UserAccount.builder()
                .userId("user4").name("박민준")
                .maxAccumulationPoint(100000L).maxRetentionPoint(1000000L)
                .build());

        // user5: 다건 적립 후 전액 사용 케이스
        userAccountRepository.save(UserAccount.builder()
                .userId("user5").name("최수진")
                .maxAccumulationPoint(100000L).maxRetentionPoint(1000000L)
                .build());

        // ── user1: 일반 적립 + 사용 + 부분 취소 ─────────────────────
        String u1p1 = pointService.accumulate("user1", 5000L, PointSourceType.ACCUMULATION, PointType.FREE, 365, "ORD-U1-001");
        String u1p2 = pointService.accumulate("user1", 3000L, PointSourceType.ACCUMULATION, PointType.FREE, 180, "ORD-U1-002");
        String u1p3 = pointService.accumulate("user1", 2000L, PointSourceType.MANUAL, PointType.FREE, null, null); // 수기 지급
        pointService.use("user1", "USE-U1-001", 4000L);   // MANUAL 우선 차감
        pointService.use("user1", "USE-U1-002", 3000L);
        pointService.cancelUsage("USE-U1-001", 1000L);    // 부분 취소
        log.info("[DataInitializer] user1 데이터 삽입 완료. pointKeys: {}, {}, {}", u1p1, u1p2, u1p3);

        // ── user2: 수기 지급 + 만료된 포인트 강제 처리 ──────────────
        String u2p1 = pointService.accumulate("user2", 10000L, PointSourceType.MANUAL, PointType.FREE, null, null);
        String u2p2 = pointService.accumulate("user2", 5000L, PointSourceType.ACCUMULATION, PointType.FREE, 30, "ORD-U2-001");
        // 만료 강제 처리
        pointRepository.findByPointKey(u2p2).ifPresent(p -> {
            p.setExpiryDateTime(java.time.LocalDateTime.now().minusDays(1));
            long remaining = p.getRemainingPoint();
            p.expire();
            pointRepository.save(p);
            if (remaining > 0) {
                pointEventRepository.save(PointEvent.builder()
                        .point(p)
                        .pointEventType(PointEventType.EXPIRE)
                        .amount(remaining)
                        .build());
            }
        });
        pointService.use("user2", "USE-U2-001", 8000L);
        pointService.cancelUsage("USE-U2-001", 5000L);    // 만료된 포인트 포함 취소 → AUTO_RESTORED 발생
        log.info("[DataInitializer] user2 데이터 삽입 완료. pointKeys: {}, {}", u2p1, u2p2);

        // ── user3: 다건 적립 + 전액 사용 + 전액 취소 ────────────────
        String u3p1 = pointService.accumulate("user3", 1000L, PointSourceType.ACCUMULATION, PointType.FREE, 90, "ORD-U3-001");
        String u3p2 = pointService.accumulate("user3", 2000L, PointSourceType.ACCUMULATION, PointType.FREE, 60, "ORD-U3-002");
        String u3p3 = pointService.accumulate("user3", 3000L, PointSourceType.ACCUMULATION, PointType.FREE, 30, "ORD-U3-003");
        pointService.use("user3", "USE-U3-001", 6000L);   // 3건 모두 소진
        pointService.cancelUsage("USE-U3-001", 6000L);    // 전액 취소
        log.info("[DataInitializer] user3 데이터 삽입 완료. pointKeys: {}, {}, {}", u3p1, u3p2, u3p3);

        // ── user4: 적립 취소 케이스 ──────────────────────────────────
        String u4p1 = pointService.accumulate("user4", 7000L, PointSourceType.ACCUMULATION, PointType.FREE, 365, "ORD-U4-001");
        String u4p2 = pointService.accumulate("user4", 3000L, PointSourceType.ACCUMULATION, PointType.FREE, 365, "ORD-U4-002");
        pointService.cancelAccumulation(u4p2);             // u4p2 적립 취소 (미사용 상태)
        pointService.use("user4", "USE-U4-001", 3000L);   // u4p1에서만 사용
        log.info("[DataInitializer] user4 데이터 삽입 완료. pointKeys: {}, {}", u4p1, u4p2);

        // ── user5: 여러 번 부분 취소 ─────────────────────────────────
        String u5p1 = pointService.accumulate("user5", 50000L, PointSourceType.ACCUMULATION, PointType.FREE, 365, "ORD-U5-001");
        String u5p2 = pointService.accumulate("user5", 20000L, PointSourceType.MANUAL, PointType.FREE, null, null);
        pointService.use("user5", "USE-U5-001", 30000L);
        pointService.use("user5", "USE-U5-002", 10000L);
        pointService.cancelUsage("USE-U5-001", 5000L);    // 1차 부분 취소
        pointService.cancelUsage("USE-U5-001", 10000L);   // 2차 부분 취소
        pointService.cancelUsage("USE-U5-002", 10000L);   // 전액 취소
        log.info("[DataInitializer] user5 데이터 삽입 완료. pointKeys: {}, {}", u5p1, u5p2);

        // ── user6: 만료 후 취소로 인한 재지급(AUTO_RESTORED) 케이스 ──
        // user6 생성
        userAccountRepository.save(UserAccount.builder()
                .userId("user6").name("정재원")
                .maxAccumulationPoint(100000L).maxRetentionPoint(1000000L)
                .build());

        // 1) 포인트 적립 (만료 기간 짧게)
        String u6p1 = pointService.accumulate("user6", 8000L, PointSourceType.ACCUMULATION, PointType.FREE, 30, "ORD-U6-001");
        String u6p2 = pointService.accumulate("user6", 4000L, PointSourceType.ACCUMULATION, PointType.FREE, 30, "ORD-U6-002");

        // 2) 포인트 사용 (u6p1, u6p2 모두 소진)
        pointService.use("user6", "USE-U6-001", 12000L);

        // 3) u6p1, u6p2 강제 만료 처리 (사용 시점 이후 만료된 것으로 시뮬레이션)
        pointRepository.findByPointKey(u6p1).ifPresent(p -> {
            p.setExpiryDateTime(java.time.LocalDateTime.now().minusDays(1));
            long remaining = p.getRemainingPoint();
            p.expire();
            pointRepository.save(p);
            if (remaining > 0) {
                pointEventRepository.save(PointEvent.builder()
                        .point(p)
                        .pointEventType(PointEventType.EXPIRE)
                        .amount(remaining)
                        .build());
            }
        });
        pointRepository.findByPointKey(u6p2).ifPresent(p -> {
            p.setExpiryDateTime(java.time.LocalDateTime.now().minusDays(1));
            long remaining = p.getRemainingPoint();
            p.expire();
            pointRepository.save(p);
            if (remaining > 0) {
                pointEventRepository.save(PointEvent.builder()
                        .point(p)
                        .pointEventType(PointEventType.EXPIRE)
                        .amount(remaining)
                        .build());
            }
        });

        // 4) 사용 취소 → 만료된 포인트이므로 AUTO_RESTORED로 신규 적립됨
        pointService.cancelUsage("USE-U6-001", 12000L);
        log.info("[DataInitializer] user6 데이터 삽입 완료 (AUTO_RESTORED 케이스). pointKeys: {}, {}", u6p1, u6p2);

        // ── user7: 만료 시 잔액(expiredPoint) 존재 + 취소로 인한 AUTO_RESTORED 케이스 ──
        // user7 생성
        userAccountRepository.save(UserAccount.builder()
                .userId("user7").name("강지훈")
                .maxAccumulationPoint(100000L).maxRetentionPoint(1000000L)
                .build());

        // 1) 포인트 적립 (10,000)
        String u7p1 = pointService.accumulate("user7", 10000L, PointSourceType.ACCUMULATION, PointType.FREE, 30, "ORD-U7-001");

        // 2) 일부만 사용 (6,000) → 잔액 4,000 남음
        pointService.use("user7", "USE-U7-001", 6000L);

        // 3) 강제 만료 처리 → remainingPoint(4,000)가 expiredPoint로 기록되고 잔액 0이 됨
        pointRepository.findByPointKey(u7p1).ifPresent(p -> {
            p.setExpiryDateTime(java.time.LocalDateTime.now().minusDays(1));
            long remaining = p.getRemainingPoint(); // 4,000
            p.expire(); // expiredPoint = 4,000, remainingPoint = 0, isExpired = true
            pointRepository.save(p);
            if (remaining > 0) {
                pointEventRepository.save(PointEvent.builder()
                        .point(p)
                        .pointEventType(PointEventType.EXPIRE)
                        .amount(remaining)
                        .build());
            }
        });

        // 4) 사용 취소 → 원본 포인트가 만료 상태이므로 AUTO_RESTORED로 신규 적립됨
        pointService.cancelUsage("USE-U7-001", 6000L);
        log.info("[DataInitializer] user7 데이터 삽입 완료 (만료 잔액 존재 + AUTO_RESTORED 케이스). pointKey: {}", u7p1);

        log.info("[DataInitializer] 더미 데이터 삽입 완료.");
    }
}
