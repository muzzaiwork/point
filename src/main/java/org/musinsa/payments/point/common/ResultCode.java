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
    SUCCESS("E0000", "성공", HttpStatus.OK),

    // 400 Bad Request
    BAD_REQUEST("E4000", "잘못된 요청입니다.", HttpStatus.BAD_REQUEST),
    INVALID_PARAMETER("E4001", "잘못된 파라미터입니다.", HttpStatus.BAD_REQUEST),
    INVALID_ACCUMULATION_AMOUNT("E4002", "적립 금액은 1포인트 이상이어야 합니다.", HttpStatus.BAD_REQUEST),
    ACCUMULATION_LIMIT_EXCEEDED("E4003", "1회 적립 가능 한도를 초과했습니다.", HttpStatus.BAD_REQUEST),
    CANCEL_AMOUNT_EXCEEDED("E4004", "취소 금액이 원본 금액을 초과할 수 없습니다.", HttpStatus.BAD_REQUEST),

    // 404 Not Found
    NOT_FOUND("E4040", "리소스를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    USER_NOT_FOUND("E4041", "사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    POINT_NOT_FOUND("E4042", "적립 내역을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    ORDER_NOT_FOUND("E4043", "주문 내역을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

    // 409 Conflict
    CONFLICT("E4090", "비즈니스 로직 충돌이 발생했습니다.", HttpStatus.CONFLICT),
    POINT_SHORTAGE("E4091", "보유 포인트가 부족합니다.", HttpStatus.CONFLICT),
    RETENTION_LIMIT_EXCEEDED("E4092", "개인별 최대 보유 가능 포인트 한도를 초과했습니다.", HttpStatus.CONFLICT),
    ALREADY_USED("E4093", "이미 사용된 금액이 있어 취소할 수 없습니다.", HttpStatus.CONFLICT),
    ALREADY_CANCELLED("E4094", "이미 취소된 내역입니다.", HttpStatus.CONFLICT),

    // 500 Internal Server Error
    INTERNAL_SERVER_ERROR("E5000", "서버 내부 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
