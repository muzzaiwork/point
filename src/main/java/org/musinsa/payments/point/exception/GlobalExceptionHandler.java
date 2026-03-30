package org.musinsa.payments.point.exception;

import lombok.extern.slf4j.Slf4j;
import org.musinsa.payments.point.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
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
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("Illegal argument exception: {}", e.getMessage());
        return ApiResponse.error(e.getMessage());
    }

    /**
     * 비즈니스 상태 오류 (포인트 부족 등)
     */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleIllegalStateException(IllegalStateException e) {
        log.warn("Illegal state exception: {}", e.getMessage());
        return ApiResponse.error(e.getMessage());
    }

    /**
     * 그 외 예상치 못한 모든 예외
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("Unhandled exception: ", e);
        return ApiResponse.error("서버 내부 오류가 발생했습니다.");
    }
}
