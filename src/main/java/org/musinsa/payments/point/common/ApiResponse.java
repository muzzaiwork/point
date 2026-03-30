package org.musinsa.payments.point.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 공통 응답 객체
 * @param <T> 데이터 타입
 */
@Data
@NoArgsConstructor
@Schema(description = "공통 응답")
public class ApiResponse<T> {
    @Schema(description = "응답 메시지", example = "성공")
    private String message;

    @Schema(description = "응답 데이터")
    private T data;

    public ApiResponse(String message, T data) {
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(message, data);
    }

    public static <T> ApiResponse<T> success(String message) {
        return new ApiResponse<>(message, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(message, null);
    }
}
