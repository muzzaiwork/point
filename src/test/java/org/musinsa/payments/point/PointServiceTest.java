package org.musinsa.payments.point;

import org.musinsa.payments.point.domain.PointAccumulation;
import org.musinsa.payments.point.domain.User;
import org.musinsa.payments.point.repository.UserRepository;
import org.musinsa.payments.point.service.PointService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.musinsa.payments.point.repository.PointAccumulationRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
public class PointServiceTest {

    @Autowired
    private PointService pointService;

    @Autowired
    private PointAccumulationRepository accumulationRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    public void setUp() {
        // 테스트 사용자 생성
        userRepository.save(User.builder()
                .userId("user1")
                .name("테스트유저")
                .maxRetentionPoint(1000000L)
                .totalPoint(0L)
                .build());
    }

    @Test
    @DisplayName("요구사항 예시 시나리오 정밀 테스트 (A~E 키 및 만료 처리)")
    public void detailedScenarioTest() {
        // 1. 1000원 적립한다 (총 잔액 0 -> 1000 원). pointKey : A 로 할당
        String pointKeyA = pointService.accumulate("user1", 1000L, false, 365);
        
        // 2. 500원 적립한다 (총 잔액 1000 -> 1500 원). pointKey : B 로 할당
        String pointKeyB = pointService.accumulate("user1", 500L, false, 365);
        
        // 3. 주문번호 A1234 에서 1200원 사용한다 (총 잔액 1500 -> 300 원). pointKey : C 로 할당
        String pointKeyC = pointService.use("user1", "A1234", 1200L);
        
        // 상세 검증 (A에서 1000, B에서 200 사용됨)
        PointAccumulation accA = accumulationRepository.findByPointKey(pointKeyA).get();
        PointAccumulation accB = accumulationRepository.findByPointKey(pointKeyB).get();
        assertThat(accA.getRemainingAmount()).isEqualTo(0L);
        assertThat(accB.getRemainingAmount()).isEqualTo(300L);
        
        // 4. A의 적립이 만료되었다.
        accA.expireForTest();
        accumulationRepository.saveAndFlush(accA);
        
        // 5. C의 사용금액 1200원 중 1100원을 부분 사용취소 한다 (총 잔액 300 -> 1400 원)
        // pointKey : D 로 할당 (취소 내역 자체는 D, A의 만료로 인한 신규 적립은 E)
        pointService.cancelUsage(pointKeyC, 1100L);
        
        // B는 만료되지 않았기 때문에 사용가능 잔액은 300 -> 400원이 된다 (1100원 중 B에서 쓴 200원 중 100원이 복구된 것 아님, 
        // 1200원 사용 내역은 A(1000) + B(200) 이었음. 1100원 취소 시:
        // A(1000) -> 만료됨 -> 신규 적립 (E)
        // B(200 중 100) -> 복구됨 (300 -> 400)
        
        PointAccumulation updatedAccB = accumulationRepository.findByPointKey(pointKeyB).get();
        assertThat(updatedAccB.getRemainingAmount()).isEqualTo(400L);
        
        // A는 이미 만료일이 지났기 때문에 pointKey E 로 1000원이 신규적립 되어야 한다.
        // (사용자 잔액 총합으로 검증)
        User user = userRepository.findByUserId("user1").get();
        assertThat(user.getTotalPoint()).isEqualTo(1400L); // 300(기존) + 1100(취소분) = 1400
        
        // 신규 적립된 건(E)이 있는지 확인 (사용 가능한 포인트 목록에서 1000원짜리 확인)
        Long totalRemainingFromAcc = accumulationRepository.getValidTotalRemainingAmount("user1", LocalDateTime.now());
        assertThat(totalRemainingFromAcc).isEqualTo(1400L); // 400(B) + 1000(E)
        
        // C는 이제 1200원 사용금액중 100원을 부분취소 할 수 있다. (기존 1100원 취소했으므로 남은건 100원)
        pointService.cancelUsage(pointKeyC, 100L);
        
        User finalUser = userRepository.findByUserId("user1").get();
        assertThat(finalUser.getTotalPoint()).isEqualTo(1500L); // 1400 + 100
        
        Long finalTotalRemaining = accumulationRepository.getValidTotalRemainingAmount("user1", LocalDateTime.now());
        assertThat(finalTotalRemaining).isEqualTo(1500L); // B(400 + 100) + E(1000)
    }
}
