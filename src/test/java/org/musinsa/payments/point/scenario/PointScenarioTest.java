package org.musinsa.payments.point.scenario;

import org.musinsa.payments.point.domain.*;
import org.musinsa.payments.point.repository.*;
import org.musinsa.payments.point.service.PointService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 요구사항 예시 시나리오를 통합적으로 검증하는 테스트 클래스
 */
@SpringBootTest
@Transactional
public class PointScenarioTest {

    @Autowired
    private PointService pointService;

    @Autowired
    private PointRepository pointRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PointEventRepository pointUsageDetailRepository;

    @Autowired
    private OrderCancelRepository orderCancelRepository;

    @BeforeEach
    public void setUp() {
        // 테스트 사용자 생성
        userAccountRepository.save(UserAccount.builder()
                .userId("user1")
                .name("시나리오유저")
                .maxAccumulationPoint(100000L)
                .maxRetentionPoint(1000000L)
                .build());
    }

    @Test
    @DisplayName("요구사항 예시 시나리오 정밀 테스트 (A~E 키 및 만료 처리)")
    public void detailedScenarioTest() {
        System.out.println(">>> 시나리오 테스트를 시작합니다.");
        printDbState("초기 상태");

        // 1. 1000원 적립한다 (총 잔액 0 -> 1000 원). pointKey : A 로 할당
        System.out.println(">>> [STEP 1] 사용자 'user1'에게 1000P를 적립합니다. (PointKey: A)");
        String pointKeyA = pointService.accumulate("user1", 1000L, PointSourceType.ACCUMULATION, PointType.FREE, 365, "ORD-A");
        printDbState("1000원 적립 (A)");
        
        // 2. 500원 적립한다 (총 잔액 1000 -> 1500 원). pointKey : B 로 할당
        System.out.println(">>> [STEP 2] 사용자 'user1'에게 추가로 500P를 적립합니다. (PointKey: B)");
        String pointKeyB = pointService.accumulate("user1", 500L, PointSourceType.ACCUMULATION, PointType.FREE, 365, "ORD-B");
        printDbState("500원 적립 (B)");
        
        // 3. 주문번호 A1234 에서 1200원 사용한다 (총 잔액 1500 -> 300 원).
        System.out.println(">>> [STEP 3] 주문 'A1234'를 위해 1200P를 사용합니다. 선입선출 원칙에 따라 A에서 1000P, B에서 200P가 차감됩니다.");
        String orderNoC = pointService.use("user1", "A1234", 1200L);
        assertThat(orderNoC).isEqualTo("A1234");
        printDbState("1200원 사용 (C)");
        
        // 상세 검증 (A에서 1000, B에서 200 사용됨)
        System.out.println(">>> [CHECK] 각 적립건의 남은 포인트가 예상과 일치하는지 확인합니다.");
        Point accA = pointRepository.findByPointKey(pointKeyA).get();
        Point accB = pointRepository.findByPointKey(pointKeyB).get();
        assertThat(accA.getRemainingPoint()).isEqualTo(0L);
        assertThat(accB.getRemainingPoint()).isEqualTo(300L);
        
        // 4. A의 적립이 만료되었다. (강제 만료 처리)
        System.out.println(">>> [STEP 4] 적립건 A의 만료 시간이 지났다고 가정하고 만료 처리를 진행합니다.");
        accA.setExpiryDateTime(LocalDateTime.now().minusDays(1));
        accA.expire();
        pointRepository.saveAndFlush(accA);
        printDbState("적립건 A 만료 처리");
        
        // 만료 데이터 상태 확인 (테스트 코드에서 명시적으로 보여줌)
        Point expiredAccA = pointRepository.findByPointKey(pointKeyA).get();
        assertThat(expiredAccA.getExpiryDateTime()).isBefore(LocalDateTime.now());
        assertThat(expiredAccA.isExpired(LocalDateTime.now())).isTrue();
        
        // 5. C의 사용금액 1200원 중 1100원을 부분 사용취소 한다 (총 잔액 300 -> 1400 원)
        System.out.println(">>> [STEP 5] 주문 'A1234'의 사용 금액 중 1100P를 부분 취소합니다.");
        System.out.println(">>> 취소 시점에 적립건 A는 만료되었으므로, A에서 사용되었던 1000P는 신규 적립(재발급)되고 B에서 사용되었던 100P는 B로 복구됩니다.");
        pointService.cancelUsage(orderNoC, 1100L);
        printDbState("1100원 사용 취소 (부분)");
        
        // B는 만료되지 않았기 때문에 사용가능 잔액은 300 -> 500원이 된다
        System.out.println(">>> [CHECK] 적립건 B의 잔액이 복구되었는지 확인합니다. (300P -> 500P)");
        Point updatedAccB = pointRepository.findByPointKey(pointKeyB).get();
        assertThat(updatedAccB.getRemainingPoint()).isEqualTo(500L);
        
        // A는 이미 만료일이 지났기 때문에 1000원이 신규적립 되어야 한다.
        System.out.println(">>> [CHECK] 전체 사용자 잔액이 예상과 일치하는지 확인합니다. (300P + 1100P = 1400P)");
        UserAccount user = userAccountRepository.findByUserId("user1").get();
        assertThat(user.getRemainingPoint()).isEqualTo(1400L);
        
        // 신규 적립된 건이 있는지 확인 (사용 가능한 포인트 목록에서 1400원 확인)
        System.out.println(">>> [CHECK] 유효한 포인트 상세 내역의 합계가 1400P인지 확인합니다.");
        Long totalRemainingFromAcc = pointRepository.getValidTotalRemainingPoint("user1");
        assertThat(totalRemainingFromAcc).isEqualTo(1400L);
        
        // C는 이제 1200원 사용금액중 100원을 부분취소 할 수 있다. (기존 1100원 취소했으므로 남은건 100원)
        System.out.println(">>> [STEP 6] 남은 사용 금액 100P를 마저 취소합니다. 이 금액은 B에서 사용되었던 것이므로 B로 복구됩니다.");
        pointService.cancelUsage(orderNoC, 100L);
        printDbState("100원 사용 취소 (최종)");
        
        System.out.println(">>> [CHECK] 최종 사용자 잔액이 1500P인지 확인합니다.");
        UserAccount finalUser = userAccountRepository.findByUserId("user1").get();
        assertThat(finalUser.getRemainingPoint()).isEqualTo(1500L);
        
        System.out.println(">>> [CHECK] 최종 적립건 B의 잔액이 500P로 복구되었는지 확인합니다.");
        Point finalAccB = pointRepository.findByPointKey(pointKeyB).get();
        assertThat(finalAccB.getRemainingPoint()).isEqualTo(500L);

        Long finalTotalRemaining = pointRepository.getValidTotalRemainingPoint("user1");
        assertThat(finalTotalRemaining).isEqualTo(1500L);
        
        System.out.println(">>> 시나리오 테스트가 성공적으로 완료되었습니다.");
    }

    private void printDbState(String title) {
        System.out.println("\n===== [ " + title + " ] =====");
        
        System.out.println("\n[USER_ACCOUNT]");
        System.out.println("userId | name | remainingPoint");
        System.out.println("-------|------|---------------");
        userAccountRepository.findAll().forEach(u -> 
            System.out.printf("%s | %s | %d\n", u.getUserId(), u.getName(), u.getRemainingPoint()));

        System.out.println("\n[POINT]");
        System.out.println("pointKey | accumulatedPoint | remainingPoint | cancelled | expired | sourceType | originId | rootId | expiryDateTime");
        System.out.println("---------|------------------|----------------|-----------|---------|------------|----------|--------|---------------");
        pointRepository.findAll().forEach(p -> 
            System.out.printf("%s | %d | %d | %b | %b | %s | %s | %s | %s\n", 
                p.getPointKey(), p.getAccumulatedPoint(), p.getRemainingPoint(), p.isCancelled(), p.isExpired(), p.getPointSourceType(), p.getOriginPointId(), p.getRootPointId(), p.getExpiryDateTime()));

        System.out.println("\n[ORDERS]");
        System.out.println("orderNo | userId | orderedPoint | canceledPoint | type");
        System.out.println("--------|--------|--------------|---------------|-------");
        orderRepository.findAll().forEach(o -> 
            System.out.printf("%s | %s | %d | %d | %s\n", o.getOrderNo(), o.getUserId(), o.getOrderedPoint(), o.getCanceledPoint(), o.getType()));

        System.out.println("\n[POINT_EVENT]");
        System.out.println("id | type | sourceType | amount | orderNo | pointKey");
        System.out.println("---|------|------------|--------|---------|----------");
        pointUsageDetailRepository.findAll().forEach(d -> 
            System.out.printf("%d | %s | %s | %d | %s | %s\n", 
                d.getId(), d.getPointEventType(), d.getPointSourceType(),
                d.getAmount(),
                d.getOrder() != null ? d.getOrder().getOrderNo() : "-", 
                d.getPoint().getPointKey()));

        System.out.println("\n[ORDER_CANCEL]");
        System.out.println("id | orderNo | cancelAmount | cancelDateTime");
        System.out.println("---|---------|--------------|---------------");
        orderCancelRepository.findAll().forEach(c -> 
            System.out.printf("%d | %s | %d | %s\n", 
                c.getId(), c.getOrder().getOrderNo(), c.getCancelAmount(), c.getRegDateTime()));
        
        System.out.println("==============================\n");
    }
}
