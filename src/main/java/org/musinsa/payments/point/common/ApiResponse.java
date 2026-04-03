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
    @Schema(description = "응답 코드", example = "E0000")
    private String code;

    @Schema(description = "응답 메시지", example = "성공")
    private String message;

    @Schema(description = "응답 데이터")
    private T data;

    public ApiResponse(ResultCode resultCode, T data) {
        this.code = resultCode.getCode();
        this.message = resultCode.getMessage();
        this.data = data;
    }

    public ApiResponse(ResultCode resultCode, String customMessage, T data) {
        this.code = resultCode.getCode();
        this.message = customMessage;
        this.data = data;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ResultCode.SUCCESS, data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(ResultCode.SUCCESS, message, data);
    }

    public static <T> ApiResponse<T> success(String message) {
        return new ApiResponse<>(ResultCode.SUCCESS, message, null);
    }

    public static <T> ApiResponse<T> error(ResultCode resultCode) {
        return new ApiResponse<>(resultCode, null);
    }

    public static <T> ApiResponse<T> error(ResultCode resultCode, String message) {
        return new ApiResponse<>(resultCode, message, null);
    }
}
