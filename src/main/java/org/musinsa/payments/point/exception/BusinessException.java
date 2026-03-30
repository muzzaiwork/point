package org.musinsa.payments.point.exception;

import lombok.Getter;
import org.musinsa.payments.point.common.ResultCode;

/**
 * 비즈니스 로직 예외 클래스
 * 특정 ResultCode를 담아 예외를 발생시킨다.
 */
@Getter
public class BusinessException extends RuntimeException {
    private final ResultCode resultCode;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }

    public BusinessException(ResultCode resultCode, String customMessage) {
        super(customMessage);
        this.resultCode = resultCode;
    }
}
