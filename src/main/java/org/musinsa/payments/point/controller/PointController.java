package org.musinsa.payments.point.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.musinsa.payments.point.common.ApiResponse;
import org.musinsa.payments.point.dto.PointDto;
import org.musinsa.payments.point.service.PointService;
import org.springframework.web.bind.annotation.*;

/**
 * 포인트 시스템 API 컨트롤러.
 *
 * <p>제공 기능:
 * <ul>
 *   <li>포인트 적립 ({@code POST /points/accumulate})</li>
 *   <li>적립 취소 ({@code POST /points/accumulate/{pointKey}/cancel})</li>
 *   <li>포인트 사용 ({@code POST /points/use})</li>
 *   <li>사용 취소 ({@code POST /points/use/{orderNo}/cancel})</li>
 * </ul>
 *
 * <p>입력값 검증은 {@code @Valid}를 통해 DTO 레벨에서 1차 수행되며,
 * 비즈니스 규칙 검증은 서비스/도메인 레이어에서 수행된다.
 */
@RestController
@RequestMapping("/points")
@RequiredArgsConstructor
@Tag(name = "Point API", description = "포인트 적립 / 적립 취소 / 사용 / 사용 취소 API")
public class PointController {

    private final PointService pointService;

    /**
     * 포인트 적립.
     *
     * <p>수기 지급(MANUAL) 또는 일반 적립(ACCUMULATION) 타입으로 포인트를 적립한다.
     * 1회 최대 10만P, 개인별 최대 100만P 보유 한도가 적용된다.
     *
     * @param request 적립 요청 정보 (userId, amount, pointSourceType, type, expiryDays, orderNo)
     * @return 생성된 적립 건의 고유 키 (pointKey)
     */
    @PostMapping("/accumulate")
    @Operation(
            summary = "포인트 적립",
            description = "사용자에게 포인트를 적립합니다. 1회 최대 10만P, 개인별 최대 100만P 보유 한도가 적용됩니다."
    )
    public ApiResponse<PointDto.PointResponse> accumulate(@Valid @RequestBody PointDto.AccumulateRequest request) {
        String pointKey = pointService.accumulate(
                request.getUserId(),
                request.getAmount(),
                request.getPointSourceType(),
                request.getType(),
                request.getExpiryDays(),
                request.getOrderNo()
        );
        return ApiResponse.success("적립 성공", new PointDto.PointResponse(pointKey));
    }

    /**
     * 적립 취소.
     *
     * <p>특정 적립 건을 취소한다.
     * 이미 일부라도 사용된 포인트는 취소할 수 없다 (부분 취소 불가).
     *
     * @param pointKey 취소할 적립 건의 고유 키
     * @return 취소 성공 메시지
     */
    @PostMapping("/accumulate/{pointKey}/cancel")
    @Operation(
            summary = "적립 취소",
            description = "특정 적립 건을 취소합니다. 이미 사용된 포인트가 있는 경우(부분 사용 포함) 취소할 수 없습니다."
    )
    public ApiResponse<Void> cancelAccumulation(
            @Parameter(description = "적립 시 발급된 pointKey", example = "20260401000001")
            @PathVariable String pointKey) {
        pointService.cancelAccumulation(pointKey);
        return ApiResponse.success("적립 취소 성공");
    }

    /**
     * 포인트 사용.
     *
     * <p>수기 지급(MANUAL) 포인트를 우선 차감하고, 이후 만료일 임박 순으로 차감한다.
     * 사용 내역은 주문 번호(orderNo)로 식별된다.
     *
     * @param request 사용 요청 정보 (userId, orderNo, amount)
     * @return 사용 성공 메시지 (data: null)
     */
    @PostMapping("/use")
    @Operation(
            summary = "포인트 사용",
            description = "주문 시 포인트를 사용합니다. 수기 지급 포인트가 우선 차감되며, 이후 만료일 임박 순으로 차감됩니다."
    )
    public ApiResponse<Void> use(@Valid @RequestBody PointDto.UseRequest request) {
        pointService.use(
                request.getUserId(),
                request.getOrderNo(),
                request.getAmount()
        );
        return ApiResponse.success("사용 성공");
    }

    /**
     * 사용 취소.
     *
     * <p>사용한 포인트를 취소(복구)한다. 부분 취소와 전체 취소를 모두 지원한다.
     * 만료된 적립 건은 원본 복구 대신 AUTO_RESTORED 타입의 신규 포인트로 재발급된다.
     *
     * @param orderNo 취소할 주문 번호
     * @param request 취소 요청 정보 (amount)
     * @return 취소 성공 메시지 (data: null)
     */
    @PostMapping("/use/{orderNo}/cancel")
    @Operation(
            summary = "사용 취소",
            description = "사용한 포인트를 취소(복구)합니다. 부분 취소 가능하며, 만료된 포인트는 신규 적립으로 처리됩니다."
    )
    public ApiResponse<Void> cancelUsage(
            @Parameter(description = "사용 시 사용된 주문 번호", example = "A1234")
            @PathVariable String orderNo,
            @Valid @RequestBody PointDto.CancelUsageRequest request) {
        pointService.cancelUsage(orderNo, request.getAmount());
        return ApiResponse.success("사용 취소 성공");
    }
}
