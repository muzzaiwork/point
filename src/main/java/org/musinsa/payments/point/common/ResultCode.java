package org.musinsa.payments.point.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * API 응답 코드 관리 Enum
 */
@Getter
@RequiredArgsConstructor
public enum ResultCode {
    SUCCESS("SUCCESS", "성공", HttpStatus.OK),
    
    BAD_REQUEST("BAD_REQUEST", "잘못된 요청입니다.", HttpStatus.BAD_REQUEST),
    INVALID_PARAMETER("INVALID_PARAMETER", "잘못된 파라미터입니다.", HttpStatus.BAD_REQUEST),
    
    CONFLICT("CONFLICT", "비즈니스 로직 충돌이 발생했습니다.", HttpStatus.CONFLICT),
    POINT_SHORTAGE("POINT_SHORTAGE", "포인트 잔액이 부족합니다.", HttpStatus.CONFLICT),
    LIMIT_EXCEEDED("LIMIT_EXCEEDED", "보유 한도를 초과했습니다.", HttpStatus.CONFLICT),
    
    NOT_FOUND("NOT_FOUND", "리소스를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
