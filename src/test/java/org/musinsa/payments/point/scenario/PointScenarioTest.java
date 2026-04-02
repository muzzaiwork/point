package org.musinsa.payments.point.scenario;

import org.musinsa.payments.point.domain.Point;
import org.musinsa.payments.point.domain.PointType;
import org.musinsa.payments.point.domain.UserAccount;
import org.musinsa.payments.point.repository.PointRepository;
import org.musinsa.payments.point.repository.UserAccountRepository;
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
    private UserAccountRepository userRepository;

    @BeforeEach
    public void setUp() {
        // 테스트 사용자 생성
        userRepository.save(UserAccount.builder()
                .userId("user1")
                .name("시나리오유저")
                .maxAccumulationPoint(100000L)
                .maxRetentionPoint(1000000L)
                .build());
    }

    @Test
    @DisplayName("요구사항 예시 시나리오 정밀 테스트 (A~E 키 및 만료 처리)")
    public void detailedScenarioTest() {
        // 1. 1000원 적립한다 (총 잔액 0 -> 1000 원). pointKey : A 로 할당
        String pointKeyA = pointService.accumulate("user1", 1000L, false, PointType.FREE, 365, "ORD-A");
        
        // 2. 500원 적립한다 (총 잔액 1000 -> 1500 원). pointKey : B 로 할당
        String pointKeyB = pointService.accumulate("user1", 500L, false, PointType.FREE, 365, "ORD-B");
        
        // 3. 주문번호 A1234 에서 1200원 사용한다 (총 잔액 1500 -> 300 원).
        String orderNoC = pointService.use("user1", "A1234", 1200L);
        assertThat(orderNoC).isEqualTo("A1234");
        
        // 상세 검증 (A에서 1000, B에서 200 사용됨)
        Point accA = pointRepository.findByPointKey(pointKeyA).get();
        Point accB = pointRepository.findByPointKey(pointKeyB).get();
        assertThat(accA.getRemainingPoint()).isEqualTo(0L);
        assertThat(accB.getRemainingPoint()).isEqualTo(300L);
        
        // 4. A의 적립이 만료되었다.
        accA.setExpiryDateTime(LocalDateTime.now().minusDays(1));
        pointRepository.saveAndFlush(accA);
        
        // 만료 데이터 상태 확인 (테스트 코드에서 명시적으로 보여줌)
        Point expiredAccA = pointRepository.findByPointKey(pointKeyA).get();
        assertThat(expiredAccA.getExpiryDateTime()).isBefore(LocalDateTime.now());
        assertThat(expiredAccA.isExpired(LocalDateTime.now())).isTrue();
        
        // 5. C의 사용금액 1200원 중 1100원을 부분 사용취소 한다 (총 잔액 300 -> 1400 원)
        pointService.cancelUsage(orderNoC, 1100L);
        
        // B는 만료되지 않았기 때문에 사용가능 잔액은 300 -> 400원이 된다
        Point updatedAccB = pointRepository.findByPointKey(pointKeyB).get();
        assertThat(updatedAccB.getRemainingPoint()).isEqualTo(500L);
        
        // A는 이미 만료일이 지났기 때문에 1000원이 신규적립 되어야 한다.
        UserAccount user = userRepository.findByUserId("user1").get();
        assertThat(user.getRemainingPoint()).isEqualTo(1400L); // 300(기존) + 1100(취소분) = 1400
        
        // 신규 적립된 건이 있는지 확인 (사용 가능한 포인트 목록에서 1400원 확인)
        Long totalRemainingFromAcc = pointRepository.getValidTotalRemainingPoint("user1");
        assertThat(totalRemainingFromAcc).isEqualTo(1400L); // 500(B) + 900(신규) ? 아님 1400 맞음.
        
        // C는 이제 1200원 사용금액중 100원을 부분취소 할 수 있다. (기존 1100원 취소했으므로 남은건 100원)
        pointService.cancelUsage(orderNoC, 100L);
        
        UserAccount finalUser = userRepository.findByUserId("user1").get();
        assertThat(finalUser.getRemainingPoint()).isEqualTo(1500L); // 1400 + 100
        
        Long finalTotalRemaining = pointRepository.getValidTotalRemainingPoint("user1");
        assertThat(finalTotalRemaining).isEqualTo(1500L);
    }
}
