package org.musinsa.payments.point.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 공통 API 응답 래퍼 클래스.
 *
 * <p>모든 API 응답은 이 클래스를 통해 일관된 형식으로 반환된다.
 * <pre>
 * {
 *   "code":    "E0000",
 *   "message": "적립 성공",
 *   "data":    { ... }
 * }
 * </pre>
 *
 * <p>정적 팩토리 메서드:
 * <ul>
 *   <li>{@code success(data)}: 성공 응답 (data 포함)</li>
 *   <li>{@code success(message, data)}: 성공 응답 (커스텀 메시지 + data)</li>
 *   <li>{@code success(message)}: 성공 응답 (data null)</li>
 *   <li>{@code error(resultCode)}: 에러 응답 (data null)</li>
 *   <li>{@code error(resultCode, message)}: 에러 응답 (커스텀 메시지)</li>
 * </ul>
 *
 * @param <T> 응답 데이터 타입
 */
@Data
@NoArgsConstructor
@Schema(description = "공통 API 응답")
public class ApiResponse<T> {

    @Schema(description = "응답 코드 (E0000: 성공, E4xxx: 클라이언트 오류, E5xxx: 서버 오류)", example = "E0000")
    private String code;

    @Schema(description = "응답 메시지", example = "적립 성공")
    private String message;

    @Schema(description = "응답 데이터 (없으면 null)")
    private T data;

    private ApiResponse(ResultCode resultCode, T data) {
        this.code    = resultCode.getCode();
        this.message = resultCode.getMessage();
        this.data    = data;
    }

    private ApiResponse(ResultCode resultCode, String customMessage, T data) {
        this.code    = resultCode.getCode();
        this.message = customMessage;
        this.data    = data;
    }

    /** 성공 응답 (data 포함, 기본 메시지 사용) */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ResultCode.SUCCESS, data);
    }

    /** 성공 응답 (커스텀 메시지 + data 포함) */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(ResultCode.SUCCESS, message, data);
    }

    /** 성공 응답 (data null, 커스텀 메시지만) */
    public static <T> ApiResponse<T> success(String message) {
        return new ApiResponse<>(ResultCode.SUCCESS, message, null);
    }

    /** 에러 응답 (data null, ResultCode 기본 메시지 사용) */
    public static <T> ApiResponse<T> error(ResultCode resultCode) {
        return new ApiResponse<>(resultCode, null);
    }

    /** 에러 응답 (data null, 커스텀 메시지 사용) */
    public static <T> ApiResponse<T> error(ResultCode resultCode, String message) {
        return new ApiResponse<>(resultCode, message, null);
    }
}
