package org.musinsa.payments.point.exception;

import lombok.Getter;
import org.musinsa.payments.point.common.ResultCode;

/**
 * 비즈니스 로직 위반 시 발생하는 커스텀 예외 클래스.
 *
 * <p>{@link ResultCode}를 통해 에러 코드와 HTTP 상태 코드를 함께 관리한다.
 * {@link GlobalExceptionHandler}에서 포착하여 일관된 {@code ApiResponse} 형식으로 응답한다.
 *
 * <p>사용 예:
 * <pre>
 *   throw new BusinessException(ResultCode.USER_NOT_FOUND);
 *   throw new BusinessException(ResultCode.ACCUMULATION_LIMIT_EXCEEDED, "1회 한도(10만)를 초과하였습니다.");
 * </pre>
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 에러 코드 및 HTTP 상태 코드를 담은 ResultCode */
    private final ResultCode resultCode;

    /**
     * ResultCode의 기본 메시지를 사용하는 생성자.
     *
     * @param resultCode 에러 코드
     */
    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }

    /**
     * 커스텀 메시지를 사용하는 생성자.
     * 한도 초과 등 동적인 메시지가 필요한 경우 사용한다.
     *
     * @param resultCode    에러 코드
     * @param customMessage 커스텀 에러 메시지
     */
    public BusinessException(ResultCode resultCode, String customMessage) {
        super(customMessage);
        this.resultCode = resultCode;
    }
}
