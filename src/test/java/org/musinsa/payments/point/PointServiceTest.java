package org.musinsa.payments.point;

import org.musinsa.payments.point.domain.PointType;
import org.musinsa.payments.point.exception.BusinessException;
import org.musinsa.payments.point.common.ResultCode;
import org.musinsa.payments.point.domain.Point;
import org.musinsa.payments.point.domain.User;
import org.musinsa.payments.point.repository.UserRepository;
import org.musinsa.payments.point.service.PointService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.musinsa.payments.point.repository.PointRepository;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
public class PointServiceTest {

    @Autowired
    private PointService pointService;

    @Autowired
    private PointRepository pointRepository;

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
    @DisplayName("포인트 적립 성공")
    public void accumulateSuccess() {
        // given
        String userId = "user1";
        Long amount = 1000L;
        
        // when
        String pointKey = pointService.accumulate(userId, amount, false, "FREE", 365);
        
        // then
        Point point = pointRepository.findByPointKey(pointKey).get();
        assertThat(point.getAmount()).isEqualTo(amount);
        assertThat(point.getRemainingAmount()).isEqualTo(amount);
        assertThat(point.getType()).isEqualTo(PointType.FREE);
        
        User user = userRepository.findByUserId(userId).get();
        assertThat(user.getTotalPoint()).isEqualTo(amount);
    }

    @Test
    @DisplayName("포인트 적립 실패 - 한도 초과 (1회)")
    public void accumulateFail_MaxAccumulationPerTime() {
        // given
        String userId = "user1";
        Long amount = 200000L; // 기본 설정 10만 초과
        
        // when & then
        assertThatThrownBy(() -> pointService.accumulate(userId, amount, false, "FREE", 365))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("resultCode", ResultCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("포인트 적립 실패 - 보유 한도 초과")
    public void accumulateFail_MaxRetention() {
        // given
        String userId = "user1";
        pointService.accumulate(userId, 100000L, false, "FREE", 365);
        pointService.accumulate(userId, 100000L, false, "FREE", 365);
        // ... (사용자의 기본 보유 한도는 100만으로 설정되어 있음)
        
        // when & then (보유 한도 100만 초과 시도)
        assertThatThrownBy(() -> pointService.accumulate(userId, 1000000L, false, "FREE", 365))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("resultCode", ResultCode.LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("적립 취소 성공")
    public void cancelAccumulationSuccess() {
        // given
        String pointKey = pointService.accumulate("user1", 1000L, false, "FREE", 365);
        
        // when
        pointService.cancelAccumulation(pointKey);
        
        // then
        Point point = pointRepository.findByPointKey(pointKey).get();
        assertThat(point.isCancelled()).isTrue();
        assertThat(point.getRemainingAmount()).isEqualTo(0L);
        
        User user = userRepository.findByUserId("user1").get();
        assertThat(user.getTotalPoint()).isEqualTo(0L);
    }

    @Test
    @DisplayName("적립 취소 실패 - 이미 사용된 포인트")
    public void cancelAccumulationFail_AlreadyUsed() {
        // given
        String pointKey = pointService.accumulate("user1", 1000L, false, "FREE", 365);
        pointService.use("user1", "ORD-1", 500L);
        
        // when & then
        assertThatThrownBy(() -> pointService.cancelAccumulation(pointKey))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("resultCode", ResultCode.CONFLICT);
    }

    @Test
    @DisplayName("포인트 사용 성공")
    public void useSuccess() {
        // given
        pointService.accumulate("user1", 1000L, false, "FREE", 365);
        
        // when
        String usageKey = pointService.use("user1", "ORD-1", 700L);
        
        // then
        assertThat(usageKey).isNotNull();
        User user = userRepository.findByUserId("user1").get();
        assertThat(user.getTotalPoint()).isEqualTo(300L);
    }

    @Test
    @DisplayName("포인트 사용 실패 - 잔액 부족")
    public void useFail_PointShortage() {
        // given
        pointService.accumulate("user1", 500L, false, "FREE", 365);
        
        // when & then
        assertThatThrownBy(() -> pointService.use("user1", "ORD-1", 1000L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("resultCode", ResultCode.POINT_SHORTAGE);
    }

    @Test
    @DisplayName("사용 취소(복구) 성공 - 만료되지 않은 포인트")
    public void cancelUsageSuccess_NotExpired() {
        // given
        pointService.accumulate("user1", 1000L, false, "FREE", 365);
        String usageKey = pointService.use("user1", "ORD-1", 500L);
        
        // when
        pointService.cancelUsage(usageKey, 500L);
        
        // then
        User user = userRepository.findByUserId("user1").get();
        assertThat(user.getTotalPoint()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("사용 취소(복구) 실패 - 취소 금액 초과")
    public void cancelUsageFail_AmountExceeded() {
        // given
        pointService.accumulate("user1", 1000L, false, "FREE", 365);
        String usageKey = pointService.use("user1", "ORD-1", 500L);
        
        // when & then
        assertThatThrownBy(() -> pointService.cancelUsage(usageKey, 600L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("resultCode", ResultCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("요구사항 예시 시나리오 정밀 테스트 (A~E 키 및 만료 처리)")
    public void detailedScenarioTest() {
        // 1. 1000원 적립한다 (총 잔액 0 -> 1000 원). pointKey : A 로 할당
        String pointKeyA = pointService.accumulate("user1", 1000L, false, "FREE", 365);
        
        // 2. 500원 적립한다 (총 잔액 1000 -> 1500 원). pointKey : B 로 할당
        String pointKeyB = pointService.accumulate("user1", 500L, false, "FREE", 365);
        
        // 3. 주문번호 A1234 에서 1200원 사용한다 (총 잔액 1500 -> 300 원). pointKey : C 로 할당
        String pointKeyC = pointService.use("user1", "A1234", 1200L);
        
        // 상세 검증 (A에서 1000, B에서 200 사용됨)
        Point accA = pointRepository.findByPointKey(pointKeyA).get();
        Point accB = pointRepository.findByPointKey(pointKeyB).get();
        assertThat(accA.getRemainingAmount()).isEqualTo(0L);
        assertThat(accB.getRemainingAmount()).isEqualTo(300L);
        
        // 4. A의 적립이 만료되었다.
        accA.setExpiryDate(LocalDateTime.now().minusDays(1));
        pointRepository.saveAndFlush(accA);
        
        // 만료 데이터 상태 확인 (테스트 코드에서 명시적으로 보여줌)
        Point expiredAccA = pointRepository.findByPointKey(pointKeyA).get();
        assertThat(expiredAccA.getExpiryDate()).isBefore(LocalDateTime.now());
        assertThat(expiredAccA.isExpired(LocalDateTime.now())).isTrue();
        
        // 5. C의 사용금액 1200원 중 1100원을 부분 사용취소 한다 (총 잔액 300 -> 1400 원)
        // pointKey : D 로 할당 (취소 내역 자체는 D, A의 만료로 인한 신규 적립은 E)
        pointService.cancelUsage(pointKeyC, 1100L);
        
        // B는 만료되지 않았기 때문에 사용가능 잔액은 300 -> 400원이 된다 (1100원 중 B에서 쓴 200원 중 100원이 복구된 것 아님, 
        // 1200원 사용 내역은 A(1000) + B(200) 이었음. 1100원 취소 시:
        // A(1000) -> 만료됨 -> 신규 적립 (E)
        // B(200 중 100) -> 복구됨 (300 -> 400)
        
        Point updatedAccB = pointRepository.findByPointKey(pointKeyB).get();
        assertThat(updatedAccB.getRemainingAmount()).isEqualTo(400L);
        
        // A는 이미 만료일이 지났기 때문에 pointKey E 로 1000원이 신규적립 되어야 한다.
        // (사용자 잔액 총합으로 검증)
        User user = userRepository.findByUserId("user1").get();
        assertThat(user.getTotalPoint()).isEqualTo(1400L); // 300(기존) + 1100(취소분) = 1400
        
        // 신규 적립된 건(E)이 있는지 확인 (사용 가능한 포인트 목록에서 1000원짜리 확인)
        Long totalRemainingFromAcc = pointRepository.getValidTotalRemainingAmount("user1", LocalDateTime.now());
        assertThat(totalRemainingFromAcc).isEqualTo(1400L); // 400(B) + 1000(E)
        
        // C는 이제 1200원 사용금액중 100원을 부분취소 할 수 있다. (기존 1100원 취소했으므로 남은건 100원)
        pointService.cancelUsage(pointKeyC, 100L);
        
        User finalUser = userRepository.findByUserId("user1").get();
        assertThat(finalUser.getTotalPoint()).isEqualTo(1500L); // 1400 + 100
        
        Long finalTotalRemaining = pointRepository.getValidTotalRemainingAmount("user1", LocalDateTime.now());
        assertThat(finalTotalRemaining).isEqualTo(1500L); // B(400 + 100) + E(1000)
    }
}
