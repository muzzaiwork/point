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
                .build());
    }

    @Test
    @DisplayName("포인트 적립 및 사용 예시 시나리오 테스트")
    public void scenarioTest() {
        // 1. 1000원 적립한다 (총 잔액 0 -> 1000 원)
        String pointKeyA = pointService.accumulate("user1", 1000L, false, 365);
        
        // 2. 500원 적립한다 (총 잔액 1000 -> 1500 원)
        String pointKeyB = pointService.accumulate("user1", 500L, false, 365);
        
        // 3. 주문번호 A1234 에서 1200원 사용한다 (총 잔액 1500 -> 300 원)
        String pointKeyC = pointService.use("user1", "A1234", 1200L);
        
        PointAccumulation accA = accumulationRepository.findByPointKey(pointKeyA).get();
        PointAccumulation accB = accumulationRepository.findByPointKey(pointKeyB).get();
        
        assertThat(accA.getRemainingAmount()).isEqualTo(0L); // A 적립에서 1000원 사용
        assertThat(accB.getRemainingAmount()).isEqualTo(300L); // B 적립에서 200원 사용
        
        // 4. A의 적립이 만료되었다고 가정
        accA.expireForTest();
        accumulationRepository.saveAndFlush(accA);
        
        // 5. C의 사용금액 1200원 중 1100원을 부분 사용취소 한다 (총 잔액 300 -> 1400 원)
        pointService.cancelUsage(pointKeyC, 1100L);
        
        // B는 만료되지 않았기 때문에 사용가능 잔액은 300 -> 400원이 된다.
        PointAccumulation updatedAccB = accumulationRepository.findByPointKey(pointKeyB).get();
        assertThat(updatedAccB.getRemainingAmount()).isEqualTo(400L);
        
        // A는 이미 만료일이 지났기 때문에 신규적립 되어야 한다. (사용자 잔액 총합으로 검증)
        Long totalRemaining = accumulationRepository.getValidTotalRemainingAmount("user1", LocalDateTime.now());
        assertThat(totalRemaining).isEqualTo(1400L); // 400(B) + 1000(신규)
    }
}
