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
}
