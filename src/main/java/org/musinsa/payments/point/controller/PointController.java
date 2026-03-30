package org.musinsa.payments.point.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.musinsa.payments.point.common.ApiResponse;
import org.musinsa.payments.point.dto.PointDto;
import org.musinsa.payments.point.service.PointService;
import org.springframework.web.bind.annotation.*;

/**
 * 포인트 시스템 API 컨트롤러
 */
@RestController
@RequestMapping("/api/points")
@RequiredArgsConstructor
@Tag(name = "Point API", description = "무료 포인트 시스템 API (적립, 사용, 취소)")
public class PointController {

    private final PointService pointService;

    /**
     * 포인트 적립
     * @param request 적립 요청 정보
     * @return 적립된 포인트의 식별 키(pointKey)
     */
    @PostMapping("/accumulate")
    @Operation(summary = "포인트 적립", description = "사용자에게 포인트를 적립합니다. (1회 최대 10만, 개인별 최대 100만 보유 가능)")
    public ApiResponse<String> accumulate(@RequestBody PointDto.AccumulateRequest request) {
        String pointKey = pointService.accumulate(
                request.getUserId(),
                request.getAmount(),
                request.isManual(),
                request.getExpiryDays()
        );
        return ApiResponse.success("적립 성공", pointKey);
    }

    /**
     * 적립 취소
     * @param pointKey 취소할 적립 건의 식별 키
     * @return 취소 결과 메시지
     */
    @PostMapping("/accumulate/{pointKey}/cancel")
    @Operation(summary = "적립 취소", description = "특정 적립 건을 취소합니다. 이미 사용된 포인트가 있는 경우 취소할 수 없습니다.")
    public ApiResponse<Void> cancelAccumulation(
            @Parameter(description = "적립 시 발급된 pointKey") @PathVariable String pointKey) {
        pointService.cancelAccumulation(pointKey);
        return ApiResponse.success("적립 취소 성공");
    }

    /**
     * 포인트 사용
     * @param request 사용 요청 정보
     * @return 사용 내역 식별 키(pointKey)
     */
    @PostMapping("/use")
    @Operation(summary = "포인트 사용", description = "주문 시 포인트를 사용합니다. 수기 지급 포인트가 우선 사용되며, 만료일이 짧은 순서로 차감됩니다.")
    public ApiResponse<String> use(@RequestBody PointDto.UseRequest request) {
        String pointKey = pointService.use(
                request.getUserId(),
                request.getOrderNo(),
                request.getAmount()
        );
        return ApiResponse.success("사용 성공", pointKey);
    }

    /**
     * 사용 취소
     * @param pointKey 사용 시 발급된 식별 키
     * @param request 취소 요청 정보 (금액 포함)
     * @return 취소 결과 메시지
     */
    @PostMapping("/use/{pointKey}/cancel")
    @Operation(summary = "사용 취소", description = "사용한 포인트를 취소(복구)합니다. 이미 만료된 포인트는 신규 적립 처리됩니다.")
    public ApiResponse<Void> cancelUsage(
            @Parameter(description = "사용 시 발급된 pointKey") @PathVariable String pointKey,
            @RequestBody PointDto.CancelUsageRequest request) {
        pointService.cancelUsage(pointKey, request.getAmount());
        return ApiResponse.success("사용 취소 성공");
    }
}
