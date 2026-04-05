package org.musinsa.payments.point.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.musinsa.payments.point.domain.PointSourceType;
import org.musinsa.payments.point.domain.PointType;

/**
 * 포인트 관련 데이터 전달 객체(DTO) 모음.
 *
 * <p>각 내부 클래스는 API 요청/응답 단위로 분리되어 있으며,
 * Jakarta Bean Validation 어노테이션으로 입력값 1차 검증을 수행합니다.
 */
public class PointDto {

    // =========================================================================
    // 요청 DTO
    // =========================================================================

    /**
     * 포인트 적립 요청.
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
        @Schema(description = "포인트 타입 (FREE: 무료, PAID: 유료)", example = "FREE")
        private PointType type;

        @Schema(description = "만료일 수 (미입력 시 2999-12-31로 설정)", example = "30")
        private Integer expiryDays;

        @Schema(description = "적립 근거 주문 번호 (선택)", example = "ORD202604010001")
        private String orderNo;
    }

    /**
     * 포인트 사용 요청.
     */
    @Data
    @NoArgsConstructor
    @Schema(description = "포인트 사용 요청")
    public static class UseRequest {

        @NotBlank(message = "사용자 ID는 필수입니다.")
        @Schema(description = "사용자 ID", example = "yonkum")
        private String userId;

        @NotBlank(message = "주문 번호는 필수입니다.")
        @Schema(description = "주문 번호 (중복 불가)", example = "A1234")
        private String orderNo;

        @NotNull(message = "사용 금액은 필수입니다.")
        @Min(value = 1, message = "사용 금액은 최소 1P 이상이어야 합니다.")
        @Schema(description = "사용 금액", example = "500")
        private Long amount;
    }

    /**
     * 포인트 사용 취소 요청.
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

    // =========================================================================
    // 응답 DTO
    // =========================================================================

    /**
     * 포인트 적립 성공 응답.
     * 생성된 적립 건의 고유 키(pointKey)를 반환한다.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "포인트 적립 성공 응답")
    public static class PointResponse {

        @Schema(description = "생성된 적립 건의 고유 키", example = "20260401000001")
        private String pointKey;
    }
}
