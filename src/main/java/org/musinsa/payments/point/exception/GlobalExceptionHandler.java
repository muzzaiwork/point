package org.musinsa.payments.point.exception;

import lombok.extern.slf4j.Slf4j;
import org.musinsa.payments.point.common.ApiResponse;
import org.musinsa.payments.point.common.ResultCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 전역 예외 핸들러
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 비즈니스 로직 오류 (잘못된 인자값 등)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("Illegal argument exception: {}", e.getMessage());
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
        log.warn("Illegal state exception: {}", e.getMessage());
        
        ResultCode resultCode = ResultCode.CONFLICT;
        // 메시지에 따라 상세 코드 매핑 (예시)
        if (e.getMessage().contains("잔액이 부족")) {
            resultCode = ResultCode.POINT_SHORTAGE;
        } else if (e.getMessage().contains("한도를 초과")) {
            resultCode = ResultCode.LIMIT_EXCEEDED;
        }

        return ResponseEntity
                .status(resultCode.getHttpStatus())
                .body(ApiResponse.error(resultCode, e.getMessage()));
    }

    /**
     * 그 외 예상치 못한 모든 예외
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unhandled exception: ", e);
        ResultCode resultCode = ResultCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity
                .status(resultCode.getHttpStatus())
                .body(ApiResponse.error(resultCode));
    }
}
