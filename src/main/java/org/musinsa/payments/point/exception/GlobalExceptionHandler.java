package org.musinsa.payments.point.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import lombok.extern.slf4j.Slf4j;
import org.musinsa.payments.point.common.ApiResponse;
import org.musinsa.payments.point.common.ResultCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 전역 예외 핸들러.
 *
 * <p>{@code @RestControllerAdvice}를 사용하여 모든 컨트롤러에서 발생하는 예외를 전역으로 포착하고,
 * 일관된 {@link ApiResponse} 형식으로 응답한다.
 *
 * <p>처리 우선순위:
 * <ol>
 *   <li>{@link BusinessException}: 비즈니스 로직 위반 (4xx)</li>
 *   <li>{@link MethodArgumentNotValidException}: Bean Validation 실패 (400)</li>
 *   <li>{@link HttpMessageNotReadableException}: JSON 파싱 오류 / Enum 불일치 (400)</li>
 *   <li>{@link IllegalArgumentException}: 잘못된 인자값 (400)</li>
 *   <li>{@link IllegalStateException}: 잘못된 상태 (409)</li>
 *   <li>{@link NoResourceFoundException}: 정적 리소스 없음 (404, 응답 바디 없음)</li>
 *   <li>{@link Exception}: 그 외 예상치 못한 모든 예외 (500, 상세 내용은 로그에만 기록)</li>
 * </ol>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 비즈니스 로직 위반 예외 처리.
     * {@link ResultCode}에 정의된 HTTP 상태 코드와 에러 코드로 응답한다.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        log.warn("[EXCEPTION] BusinessException: {} - {} | Code: {}",
                e.getResultCode(), e.getMessage(), e.getResultCode().getCode());
        ResultCode resultCode = e.getResultCode();
        return ResponseEntity
                .status(resultCode.getHttpStatus())
                .body(ApiResponse.error(resultCode, e.getMessage()));
    }

    /**
     * 잘못된 인자값 예외 처리 (400 Bad Request).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("[EXCEPTION] IllegalArgumentException: {}", e.getMessage());
        ResultCode resultCode = ResultCode.BAD_REQUEST;
        return ResponseEntity
                .status(resultCode.getHttpStatus())
                .body(ApiResponse.error(resultCode, e.getMessage()));
    }

    /**
     * 잘못된 상태 예외 처리 (409 Conflict).
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalStateException(IllegalStateException e) {
        log.warn("[EXCEPTION] IllegalStateException: {}", e.getMessage());
        ResultCode resultCode = ResultCode.CONFLICT;
        return ResponseEntity
                .status(resultCode.getHttpStatus())
                .body(ApiResponse.error(resultCode, e.getMessage()));
    }

    /**
     * Bean Validation 유효성 검사 실패 처리 (400 Bad Request).
     * 모든 필드 오류 메시지를 하나의 문자열로 합쳐 응답한다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String errorMessage = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("[EXCEPTION] MethodArgumentNotValidException: {}", errorMessage);
        ResultCode resultCode = ResultCode.BAD_REQUEST;
        return ResponseEntity
                .status(resultCode.getHttpStatus())
                .body(ApiResponse.error(resultCode, errorMessage));
    }

    /**
     * HTTP 메시지 읽기 실패 처리 (400 Bad Request).
     * JSON 파싱 오류 또는 Enum 타입 불일치 시 발생하며,
     * Jackson의 {@link InvalidFormatException}인 경우 허용된 값 목록을 포함한 상세 메시지를 반환한다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("[EXCEPTION] HttpMessageNotReadableException: {}", e.getMessage());
        ResultCode resultCode = ResultCode.BAD_REQUEST;
        String message = "잘못된 요청 형식입니다. 필드 타입을 확인해주세요.";

        // InvalidFormatException인 경우 필드명과 허용 값 목록을 포함한 상세 메시지 생성
        if (e.getCause() instanceof InvalidFormatException ife) {
            String fieldName = ife.getPath().stream()
                    .map(JsonMappingException.Reference::getFieldName)
                    .collect(Collectors.joining("."));
            if (ife.getTargetType().isEnum()) {
                String allowedValues = Arrays.toString(ife.getTargetType().getEnumConstants());
                message = String.format("잘못된 입력값입니다. 필드: '%s', 허용된 값: %s", fieldName, allowedValues);
            } else {
                message = String.format("잘못된 입력값입니다. 필드: '%s', 기대 타입: %s",
                        fieldName, ife.getTargetType().getSimpleName());
            }
        }

        return ResponseEntity
                .status(resultCode.getHttpStatus())
                .body(ApiResponse.error(resultCode, message));
    }

    /**
     * 정적 리소스 없음 처리 (404, 응답 바디 없음).
     * favicon.ico 등 불필요한 리소스 요청에 대해 빈 응답을 반환한다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFoundException(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    /**
     * 예상치 못한 모든 예외 처리 (500 Internal Server Error).
     * 보안을 위해 상세 에러 내용은 로그에만 기록하고, 클라이언트에는 일반 메시지만 반환한다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("[EXCEPTION] Unhandled Exception: ", e);
        ResultCode resultCode = ResultCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity
                .status(resultCode.getHttpStatus())
                .body(ApiResponse.error(resultCode));
    }
}
