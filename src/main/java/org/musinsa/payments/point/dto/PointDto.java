package org.musinsa.payments.point.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.musinsa.payments.point.domain.PointEventType;
import org.musinsa.payments.point.domain.PointSourceType;

import java.time.LocalDate;
import java.util.List;

/**
 * 포인트 관련 데이터 전달 객체(DTO)
 */
public class PointDto {

    /**
     * 포인트 적립 요청 객체
     */
    @Data
    @NoArgsConstructor
    @Schema(description = "포인트 적립 요청")
    public static class AccumulateRequest {
        @NotBlank(message = "사용자 ID는 필수입니다.")
        @Schema(description = "사용자 ID", example = "yonkum")
        private String userId;

        @NotNull(message = "적립 금액은 필수입니다.")
        @Min(value = 1, message = "적립 금액은 최소 1P 이상이어야 합니다.")
        @Schema(description = "적립 금액", example = "1000")
        private Long amount;

        @NotNull(message = "적립 원천 타입은 필수입니다. (ACCUMULATION, MANUAL 중 하나를 입력하세요)")
        @Schema(description = "포인트 적립 원천 타입 (ACCUMULATION: 일반 적립, MANUAL: 수기 지급)", example = "ACCUMULATION")
        private PointSourceType pointSourceType;

        @NotNull(message = "잘못된 포인트 타입입니다. (FREE, PAID 중 하나를 입력하세요)")
        @Schema(description = "포인트 타입 (FREE, PAID)", example = "FREE")
        private org.musinsa.payments.point.domain.PointType type;

        @Schema(description = "만료일 수 (미입력 시 2999-12-31)", example = "30")
        private Integer expiryDays;

        @Schema(description = "적립 근거 주문 번호", example = "ORD202604010001")
        private String orderNo;
    }

    /**
     * 포인트 사용 요청 객체
     */
    @Data
    @NoArgsConstructor
    @Schema(description = "포인트 사용 요청")
    public static class UseRequest {
        @NotBlank(message = "사용자 ID는 필수입니다.")
        @Schema(description = "사용자 ID", example = "yonkum")
        private String userId;

        @NotBlank(message = "주문 번호는 필수입니다.")
        @Schema(description = "주문 번호", example = "A1234")
        private String orderNo;

        @NotNull(message = "사용 금액은 필수입니다.")
        @Min(value = 1, message = "사용 금액은 최소 1P 이상이어야 합니다.")
        @Schema(description = "사용 금액", example = "500")
        private Long amount;
    }

    /**
     * 포인트 사용 취소 요청 객체
     */
    @Data
    @NoArgsConstructor
    @Schema(description = "포인트 사용 취소 요청")
    public static class CancelUsageRequest {
        @NotNull(message = "취소 금액은 필수입니다.")
        @Min(value = 1, message = "취소 금액은 최소 1P 이상이어야 합니다.")
        @Schema(description = "취소 금액 (부분 취소 가능)", example = "100")
        private Long amount;
    }

    /**
     * 포인트 결과 응답
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "포인트 결과 응답")
    public static class PointResponse {
        @Schema(description = "포인트 또는 주문 식별 키", example = "20260401000001")
        private String pointKey;
    }

    /**
     * 포인트 이벤트 이력 단건 응답
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "포인트 이벤트 이력")
    public static class PointEventResponse {
        @Schema(description = "이벤트 ID")
        private Long id;
        @Schema(description = "이벤트 타입 (ACCUMULATE, USE, USE_CANCEL 등)")
        private PointEventType pointEventType;
        @Schema(description = "금액")
        private Long amount;
        @Schema(description = "연관 포인트 키")
        private String pointKey;
        @Schema(description = "연관 주문 번호")
        private String orderNo;
        @Schema(description = "취소 ID")
        private Long orderCancelId;
        @Schema(description = "취소 금액")
        private Long cancelAmount;
        @Schema(description = "등록 일시")
        private String regDateTime;
    }

    /**
     * 일별 집계 응답 (정산용)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "일별 포인트 집계 (정산용)")
    public static class DailyAggregationResponse {
        @Schema(description = "집계 일자")
        private LocalDate date;
        @Schema(description = "이벤트 타입")
        private PointEventType pointEventType;
        @Schema(description = "합계 금액")
        private Long totalAmount;
    }

    /**
     * 일별 집계 조회 요청
     */
    @Data
    @NoArgsConstructor
    @Schema(description = "일별 집계 조회 요청")
    public static class DailyAggregationRequest {
        @NotNull(message = "시작 날짜는 필수입니다.")
        @Schema(description = "시작 날짜 (yyyy-MM-dd)", example = "2026-04-01")
        private LocalDate startDate;

        @NotNull(message = "종료 날짜는 필수입니다.")
        @Schema(description = "종료 날짜 (yyyy-MM-dd)", example = "2026-04-30")
        private LocalDate endDate;
    }
}
