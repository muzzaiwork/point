package org.musinsa.payments.point;

import org.musinsa.payments.point.domain.PointSourceType;
import org.musinsa.payments.point.domain.PointType;
import org.musinsa.payments.point.exception.BusinessException;
import org.musinsa.payments.point.common.ResultCode;
import org.musinsa.payments.point.domain.Point;
import org.musinsa.payments.point.domain.UserAccount;
import org.musinsa.payments.point.repository.UserAccountRepository;
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
    private UserAccountRepository userAccountRepository;

    @BeforeEach
    public void setUp() {
        // 테스트 사용자 생성
        userAccountRepository.save(UserAccount.builder()
                .userId("user1")
                .name("테스트유저")
                .maxAccumulationPoint(100000L)
                .maxRetentionPoint(1000000L)
                .build());
    }

    @Test
    @DisplayName("포인트 적립 성공")
    public void accumulateSuccess() {
        // given
        String userId = "user1";
        Long amount = 1000L;
        
        // when
        String pointKey = pointService.accumulate(userId, amount, PointSourceType.ACCUMULATION, PointType.FREE, 365, "ORD-123");
        
        // then
        Point point = pointRepository.findByPointKey(pointKey).get();
        assertThat(point.getAccumulatedPoint()).isEqualTo(amount);
        assertThat(point.getRemainingPoint()).isEqualTo(amount);
        assertThat(point.getType()).isEqualTo(PointType.FREE);
        assertThat(point.getPointSourceType()).isEqualTo(PointSourceType.ACCUMULATION);
        assertThat(point.getOrderNo()).isEqualTo("ORD-123");
        assertThat(point.getRootPointKey()).isEqualTo(point.getPointKey());
        
        UserAccount user = userAccountRepository.findByUserId(userId).get();
        assertThat(user.getRemainingPoint()).isEqualTo(amount);
    }

    @Test
    @DisplayName("포인트 적립 성공 - 만료일 수 미지정 시 2999-12-31 설정")
    public void accumulateSuccess_DefaultExpiry() {
        // given
        String userId = "user1";
        Long amount = 1000L;
        
        // when
        String pointKey = pointService.accumulate(userId, amount, PointSourceType.ACCUMULATION, PointType.FREE, null, null);
        
        // then
        Point point = pointRepository.findByPointKey(pointKey).get();
        assertThat(point.getExpiryDateTime()).isEqualTo(LocalDateTime.of(2999, 12, 31, 23, 59, 59));
    }

    @Test
    @DisplayName("포인트 적립 성공 - 만료일 수 지정 (제한 없음)")
    public void accumulateSuccess_CustomExpiry() {
        // given
        String userId = "user1";
        Long amount = 1000L;
        Integer expiryDays = 10000; // 약 27년
        
        // when
        String pointKey = pointService.accumulate(userId, amount, PointSourceType.ACCUMULATION, PointType.FREE, expiryDays, null);
        
        // then
        Point point = pointRepository.findByPointKey(pointKey).get();
        assertThat(point.getExpiryDateTime()).isAfter(LocalDateTime.now().plusYears(20));
    }

    @Test
    @DisplayName("포인트 적립 실패 - 보유 한도 초과")
    public void accumulateFail_MaxRetention() {
        // given
        String userId = "user1";
        // 10만씩 10번 적립 = 100만
        for (int i = 0; i < 10; i++) {
            pointService.accumulate(userId, 100000L, PointSourceType.ACCUMULATION, PointType.FREE, 365, null);
        }
        
        // when & then (보유 한도 100만 초과 시도)
        assertThatThrownBy(() -> pointService.accumulate(userId, 1L, PointSourceType.ACCUMULATION, PointType.FREE, 365, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("resultCode", ResultCode.RETENTION_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("적립 취소 성공")
    public void cancelAccumulationSuccess() {
        // given
        String pointKey = pointService.accumulate("user1", 1000L, PointSourceType.ACCUMULATION, PointType.FREE, 365, null);
        
        // when
        pointService.cancelAccumulation(pointKey);
        
        // then
        Point point = pointRepository.findByPointKey(pointKey).get();
        assertThat(point.isCancelled()).isTrue();
        assertThat(point.getRemainingPoint()).isEqualTo(0L);
        
        UserAccount user = userAccountRepository.findByUserId("user1").get();
        assertThat(user.getRemainingPoint()).isEqualTo(0L);
    }

    @Test
    @DisplayName("적립 취소 실패 - 이미 사용된 포인트")
    public void cancelAccumulationFail_AlreadyUsed() {
        // given
        String pointKey = pointService.accumulate("user1", 1000L, PointSourceType.ACCUMULATION, PointType.FREE, 365, null);
        pointService.use("user1", "ORD-1", 500L);
        
        // when & then
        assertThatThrownBy(() -> pointService.cancelAccumulation(pointKey))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("resultCode", ResultCode.ALREADY_USED);
    }

    @Test
    @DisplayName("포인트 사용 성공")
    public void useSuccess() {
        // given
        pointService.accumulate("user1", 1000L, PointSourceType.ACCUMULATION, PointType.FREE, 365, null);
        
        // when
        String orderNo = pointService.use("user1", "ORD-1", 700L);
        
        // then
        assertThat(orderNo).isEqualTo("ORD-1");
        UserAccount user = userAccountRepository.findByUserId("user1").get();
        assertThat(user.getRemainingPoint()).isEqualTo(300L);
    }

    @Test
    @DisplayName("포인트 사용 실패 - 잔액 부족")
    public void useFail_PointShortage() {
        // given
        pointService.accumulate("user1", 500L, PointSourceType.ACCUMULATION, PointType.FREE, 365, null);
        
        // when & then
        assertThatThrownBy(() -> pointService.use("user1", "ORD-1", 1000L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("resultCode", ResultCode.POINT_SHORTAGE);
    }

    @Test
    @DisplayName("사용 취소(복구) 성공 - 만료되지 않은 포인트")
    public void cancelUsageSuccess_NotExpired() {
        // given
        pointService.accumulate("user1", 1000L, PointSourceType.ACCUMULATION, PointType.FREE, 365, null);
        String orderNo = pointService.use("user1", "ORD-1", 500L);
        
        // when
        pointService.cancelUsage(orderNo, 500L);
        
        // then
        UserAccount user = userAccountRepository.findByUserId("user1").get();
        assertThat(user.getRemainingPoint()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("사용 취소(복구) 실패 - 취소 금액 초과")
    public void cancelUsageFail_AmountExceeded() {
        // given
        pointService.accumulate("user1", 1000L, PointSourceType.ACCUMULATION, PointType.FREE, 365, null);
        String orderNo = pointService.use("user1", "ORD-1", 500L);
        
        // when & then
        assertThatThrownBy(() -> pointService.cancelUsage(orderNo, 501L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("사용 취소(복구) 실패 - 부분 취소 후 잔여 취소 가능 금액 초과")
    public void cancelUsageFail_PartialAmountExceeded() {
        // given
        pointService.accumulate("user1", 1000L, PointSourceType.ACCUMULATION, PointType.FREE, 365, null);
        String orderNo = pointService.use("user1", "ORD-1", 500L);
        pointService.cancelUsage(orderNo, 300L); // 200L 남음
        
        // when & then
        assertThatThrownBy(() -> pointService.cancelUsage(orderNo, 201L))
                .isInstanceOf(BusinessException.class);
    }
}
