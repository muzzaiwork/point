package org.musinsa.payments.point.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import lombok.extern.slf4j.Slf4j;
import org.musinsa.payments.point.common.ApiResponse;
import org.musinsa.payments.point.common.ResultCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 전역 예외 핸들러
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 비즈니스 예외 처리
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
     * 비즈니스 로직 오류 (잘못된 인자값 등)
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
     * 비즈니스 상태 오류 (포인트 부족 등)
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
     * Bean Validation 유효성 검사 실패 처리
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
     * HTTP 메시지 읽기 실패 (JSON 파싱 오류, Enum 타입 불일치 등)
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("[EXCEPTION] HttpMessageNotReadableException: {}", e.getMessage());
        ResultCode resultCode = ResultCode.BAD_REQUEST;
        String message = "잘못된 요청 형식입니다. 필드 타입을 확인해주세요.";

        // Jackson의 InvalidFormatException인 경우 상세 정보 추출 (Enum 변환 실패 등)
        if (e.getCause() instanceof InvalidFormatException ife) {
            String fieldName = ife.getPath().stream()
                    .map(JsonMappingException.Reference::getFieldName)
                    .collect(Collectors.joining("."));

            if (ife.getTargetType().isEnum()) {
                String values = Arrays.toString(ife.getTargetType().getEnumConstants());
                message = String.format("잘못된 입력값입니다. 필드: '%s', 허용된 값: %s", fieldName, values);
            } else {
                message = String.format("잘못된 입력값입니다. 필드: '%s', 기대 타입: %s", fieldName, ife.getTargetType().getSimpleName());
            }
        }

        return ResponseEntity
                .status(resultCode.getHttpStatus())
                .body(ApiResponse.error(resultCode, message));
    }

    /**
     * 그 외 예상치 못한 모든 예외
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
