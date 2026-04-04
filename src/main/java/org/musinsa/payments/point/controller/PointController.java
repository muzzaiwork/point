package org.musinsa.payments.point.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.musinsa.payments.point.common.ApiResponse;
import org.musinsa.payments.point.dto.PointDto;
import org.musinsa.payments.point.service.PointService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 포인트 시스템 API 컨트롤러
 */
@RestController
@RequestMapping("/points")
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
     * 적립 취소
     * @param pointKey 취소할 적립 건의 식별 키
     * @return 취소 결과 메시지
     */
    @PostMapping("/accumulate/{pointKey}/cancel")
    @Operation(summary = "적립 취소", description = "특정 적립 건을 취소합니다. 이미 사용된 포인트가 있는 경우 취소할 수 없습니다.")
    public ApiResponse<Void> cancelAccumulation(
            @Parameter(description = "적립 시 발급된 pointKey", example = "20260401000001") @PathVariable String pointKey) {
        pointService.cancelAccumulation(pointKey);
        return ApiResponse.success("적립 취소 성공");
    }

    /**
     * 포인트 사용
     * @param request 사용 요청 정보
     * @return 사용 내역 식별 키(orderNo)
     */
    @PostMapping("/use")
    @Operation(summary = "포인트 사용", description = "주문 시 포인트를 사용합니다. 수기 지급 포인트가 우선 사용되며, 만료일이 짧은 순서로 차감됩니다.")
    public ApiResponse<PointDto.PointResponse> use(@Valid @RequestBody PointDto.UseRequest request) {
        String orderNo = pointService.use(
                request.getUserId(),
                request.getOrderNo(),
                request.getAmount()
        );
        return ApiResponse.success("사용 성공", new PointDto.PointResponse(orderNo));
    }

    /**
     * 사용 취소
     * @param orderNo 사용 시 사용된 주문 번호
     * @param request 취소 요청 정보 (금액 포함)
     * @return 취소 결과 메시지
     */
    @PostMapping("/use/{orderNo}/cancel")
    @Operation(summary = "사용 취소", description = "사용한 포인트를 취소(복구)합니다. 이미 만료된 포인트는 신규 적립 처리됩니다.")
    public ApiResponse<Void> cancelUsage(
            @Parameter(description = "사용 시 사용된 orderNo", example = "A1234") @PathVariable String orderNo,
            @Valid @RequestBody PointDto.CancelUsageRequest request) {
        pointService.cancelUsage(orderNo, request.getAmount());
        return ApiResponse.success("사용 취소 성공");
    }

    /**
     * 특정 포인트 건의 모든 이벤트 이력 조회
     * @param pointKey 조회할 포인트 키
     * @return 해당 포인트 건의 이벤트 이력 목록
     */
    @GetMapping("/history/point/{pointKey}")
    @Operation(summary = "포인트 건별 이력 조회", description = "특정 pointKey에 연결된 모든 이벤트 이력(적립, 사용, 취소 등)을 조회합니다.")
    public ApiResponse<List<PointDto.PointEventResponse>> getPointHistory(
            @Parameter(description = "조회할 포인트 키", example = "20260401000001") @PathVariable String pointKey) {
        return ApiResponse.success("조회 성공", pointService.getPointHistory(pointKey));
    }

    /**
     * 특정 사용자의 모든 포인트 이벤트 이력 조회
     * @param userId 조회할 사용자 ID
     * @return 해당 사용자의 전체 이벤트 이력 목록
     */
    @GetMapping("/history/user/{userId}")
    @Operation(summary = "사용자별 이력 조회", description = "특정 사용자의 모든 포인트 이벤트 이력을 조회합니다.")
    public ApiResponse<List<PointDto.PointEventResponse>> getUserHistory(
            @Parameter(description = "조회할 사용자 ID", example = "user1") @PathVariable String userId) {
        return ApiResponse.success("조회 성공", pointService.getUserHistory(userId));
    }

    /**
     * 일별 집계 조회 (정산용)
     * @param startDate 시작 날짜
     * @param endDate 종료 날짜
     * @return 날짜별 이벤트 타입별 합계 목록
     */
    @GetMapping("/history/daily")
    @Operation(summary = "일별 집계 조회 (정산용)", description = "특정 기간의 날짜별 포인트 이벤트 타입별 합계를 조회합니다.")
    public ApiResponse<List<PointDto.DailyAggregationResponse>> getDailyAggregation(
            @Parameter(description = "시작 날짜 (yyyy-MM-dd)", example = "2026-04-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "종료 날짜 (yyyy-MM-dd)", example = "2026-04-30")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResponse.success("조회 성공", pointService.getDailyAggregation(startDate, endDate));
    }
}
