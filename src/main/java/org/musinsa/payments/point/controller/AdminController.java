package org.musinsa.payments.point.controller;

import lombok.RequiredArgsConstructor;
import org.musinsa.payments.point.dto.PointDto;
import org.musinsa.payments.point.service.PointService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final PointService pointService;

    @GetMapping
    public String index() {
        return "redirect:/admin/user-history";
    }

    @GetMapping("/user-history")
    public String userHistory(
            @RequestParam(required = false) String userId,
            Model model) {
        model.addAttribute("userId", userId);
        if (userId != null && !userId.isBlank()) {
            List<PointDto.PointEventResponse> history = pointService.getUserHistory(userId);
            model.addAttribute("history", history);
        }
        return "admin/user-history";
    }

    @GetMapping("/point-history")
    public String pointHistory(
            @RequestParam(required = false) String pointKey,
            Model model) {
        model.addAttribute("pointKey", pointKey);
        if (pointKey != null && !pointKey.isBlank()) {
            List<PointDto.PointEventResponse> history = pointService.getPointHistory(pointKey);
            model.addAttribute("history", history);
        }
        return "admin/point-history";
    }

    @GetMapping("/daily")
    public String daily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Model model) {
        if (startDate == null) startDate = LocalDate.now().withDayOfMonth(1);
        if (endDate == null) endDate = LocalDate.now();
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        List<PointDto.DailyAggregationResponse> aggregation = pointService.getDailyAggregation(startDate, endDate);
        model.addAttribute("aggregation", aggregation);
        return "admin/daily";
    }
}
