package org.musinsa.payments.point.controller;

import lombok.RequiredArgsConstructor;
import org.musinsa.payments.point.domain.Order;
import org.musinsa.payments.point.domain.OrderType;
import org.musinsa.payments.point.domain.Point;
import org.musinsa.payments.point.domain.PointEvent;
import org.musinsa.payments.point.domain.PointSourceType;
import org.musinsa.payments.point.domain.PointType;
import org.musinsa.payments.point.domain.UserAccount;
import org.musinsa.payments.point.repository.OrderRepository;
import org.musinsa.payments.point.repository.PointEventRepository;
import org.musinsa.payments.point.repository.PointRepository;
import org.musinsa.payments.point.repository.UserAccountRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserAccountRepository userAccountRepository;
    private final PointRepository pointRepository;
    private final OrderRepository orderRepository;
    private final PointEventRepository pointEventRepository;

    @GetMapping
    public String index() {
        return "redirect:/admin/accounts";
    }

    @GetMapping("/accounts")
    public String accounts(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String name,
            Model model) {

        List<UserAccount> accounts;
        if ((userId != null && !userId.isBlank()) || (name != null && !name.isBlank())) {
            accounts = userAccountRepository.searchAccounts(
                    (userId != null && !userId.isBlank()) ? userId : null,
                    (name != null && !name.isBlank()) ? name : null
            );
        } else {
            accounts = userAccountRepository.findAll();
        }

        model.addAttribute("accounts", accounts);
        model.addAttribute("userId", userId);
        model.addAttribute("name", name);
        return "admin/accounts";
    }

    @GetMapping("/points")
    public String points(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            Model model) {

        String userIdParam = (userId != null && !userId.isBlank()) ? userId : null;
        Boolean cancelledParam = null;
        if ("cancelled".equals(status)) cancelledParam = true;
        else if ("active".equals(status)) cancelledParam = false;

        PointType typeParam = null;
        try { if (type != null && !type.isBlank()) typeParam = PointType.valueOf(type); } catch (Exception ignored) {}

        PointSourceType sourceTypeParam = null;
        try { if (sourceType != null && !sourceType.isBlank()) sourceTypeParam = PointSourceType.valueOf(sourceType); } catch (Exception ignored) {}

        LocalDate startDateParam = null;
        try { if (startDate != null && !startDate.isBlank()) startDateParam = LocalDate.parse(startDate); } catch (Exception ignored) {}

        LocalDate endDateParam = null;
        try { if (endDate != null && !endDate.isBlank()) endDateParam = LocalDate.parse(endDate); } catch (Exception ignored) {}

        List<Point> points = pointRepository.searchPoints(userIdParam, cancelledParam, typeParam, sourceTypeParam, startDateParam, endDateParam);

        model.addAttribute("points", points);
        model.addAttribute("userId", userId);
        model.addAttribute("status", status);
        model.addAttribute("type", type);
        model.addAttribute("sourceType", sourceType);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        return "admin/points";
    }

    @GetMapping("/orders")
    public String orders(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            Model model) {

        String userIdParam = (userId != null && !userId.isBlank()) ? userId : null;
        String orderNoParam = (orderNo != null && !orderNo.isBlank()) ? orderNo : null;

        OrderType typeParam = null;
        try { if (type != null && !type.isBlank()) typeParam = OrderType.valueOf(type); } catch (Exception ignored) {}

        LocalDateTime startDateParam = null;
        try { if (startDate != null && !startDate.isBlank()) startDateParam = LocalDate.parse(startDate).atStartOfDay(); } catch (Exception ignored) {}

        LocalDateTime endDateParam = null;
        try { if (endDate != null && !endDate.isBlank()) endDateParam = LocalDate.parse(endDate).atTime(23, 59, 59); } catch (Exception ignored) {}

        List<Order> orders = orderRepository.searchOrders(userIdParam, orderNoParam, typeParam, startDateParam, endDateParam);

        model.addAttribute("orders", orders);
        model.addAttribute("userId", userId);
        model.addAttribute("orderNo", orderNo);
        model.addAttribute("type", type);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        return "admin/orders";
    }

    @GetMapping("/point-events")
    @ResponseBody
    public ResponseEntity<List<java.util.Map<String, Object>>> pointEvents(
            @RequestParam(required = false) String pointKey) {

        List<PointEvent> events = (pointKey != null && !pointKey.isBlank())
                ? pointEventRepository.findAllByPointKey(pointKey)
                : List.of();

        List<java.util.Map<String, Object>> result = events.stream().map(e -> {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("pointEventType", e.getPointEventType().name());
            m.put("amount", e.getAmount());
            m.put("orderNo", e.getOrder() != null ? e.getOrder().getOrderNo() : null);
            m.put("orderCancelId", e.getOrderCancel() != null ? e.getOrderCancel().getId() : null);
            m.put("regDateTime", e.getRegDateTime() != null ? e.getRegDateTime().toString() : null);
            return m;
        }).toList();

        return ResponseEntity.ok(result);
    }
}
