package org.musinsa.payments.point.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

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
        @Schema(description = "사용자 ID", example = "user1")
        private String userId;

        @Schema(description = "적립 금액", example = "1000")
        private Long amount;

        @Schema(description = "관리자 수기 지급 여부", example = "false")
        private boolean isManual;

        @Schema(description = "만료일 수 (미입력 시 기본 365일)", example = "30")
        private Integer expiryDays;
    }

    /**
     * 포인트 사용 요청 객체
     */
    @Data
    @NoArgsConstructor
    @Schema(description = "포인트 사용 요청")
    public static class UseRequest {
        @Schema(description = "사용자 ID", example = "user1")
        private String userId;

        @Schema(description = "주문 번호", example = "A1234")
        private String orderNo;

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
        @Schema(description = "취소 금액 (부분 취소 가능)", example = "100")
        private Long amount;
    }

    /**
     * 공통 응답 객체
     * @param <T> 데이터 타입
     */
    @Data
    @Schema(description = "공통 응답")
    public static class Response<T> {
        @Schema(description = "응답 메시지", example = "성공")
        private String message;

        @Schema(description = "응답 데이터")
        private T data;

        public Response(String message, T data) {
            this.message = message;
            this.data = data;
        }

        public Response() {
        }
    }
}
