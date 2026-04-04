package org.musinsa.payments.point.controller;

import lombok.RequiredArgsConstructor;
import org.musinsa.payments.point.domain.Order;
import org.musinsa.payments.point.domain.OrderCancel;
import org.musinsa.payments.point.domain.OrderType;
import org.musinsa.payments.point.domain.Point;
import org.musinsa.payments.point.domain.PointEvent;
import org.musinsa.payments.point.domain.PointEventType;
import org.musinsa.payments.point.domain.PointSourceType;
import org.musinsa.payments.point.domain.PointType;
import org.musinsa.payments.point.domain.UserAccount;
import org.musinsa.payments.point.repository.OrderCancelRepository;
import org.musinsa.payments.point.repository.OrderRepository;
import org.musinsa.payments.point.repository.PointEventRepository;
import org.musinsa.payments.point.repository.PointRepository;
import org.musinsa.payments.point.repository.UserAccountRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private static final int PAGE_SIZE = 20;

    private final UserAccountRepository userAccountRepository;
    private final PointRepository pointRepository;
    private final OrderRepository orderRepository;
    private final OrderCancelRepository orderCancelRepository;
    private final PointEventRepository pointEventRepository;

    @GetMapping
    public String index() {
        return "redirect:/admin/accounts";
    }

    @GetMapping("/accounts")
    public String accounts(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        PageRequest pageable = PageRequest.of(page, PAGE_SIZE, Sort.by("regDateTime").descending());
        Page<UserAccount> accounts = userAccountRepository.searchAccounts(
                (userId != null && !userId.isBlank()) ? userId : null,
                (name != null && !name.isBlank()) ? name : null,
                pageable
        );

        int totalPages = accounts.getTotalPages();
        int startPage = Math.max(0, page - 2);
        int endPage = Math.min(totalPages - 1, page + 2);

        model.addAttribute("accounts", accounts);
        model.addAttribute("userId", userId);
        model.addAttribute("name", name);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("startPage", startPage);
        model.addAttribute("endPage", endPage);
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
            @RequestParam(defaultValue = "0") int page,
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

        PageRequest pageable = PageRequest.of(page, PAGE_SIZE);
        Page<Point> points = pointRepository.searchPoints(userIdParam, cancelledParam, typeParam, sourceTypeParam, startDateParam, endDateParam, pageable);

        Map<Long, String> restoredPointKeyMap = new HashMap<>();
        for (Point p : points) {
            pointRepository.findByOriginPointKey(p.getPointKey())
                    .ifPresent(restored -> restoredPointKeyMap.put(p.getId(), restored.getPointKey()));
        }

        int totalPages = points.getTotalPages();
        int startPage = Math.max(0, page - 2);
        int endPage = Math.min(totalPages - 1, page + 2);

        model.addAttribute("points", points);
        model.addAttribute("restoredPointKeyMap", restoredPointKeyMap);
        model.addAttribute("userId", userId);
        model.addAttribute("status", status);
        model.addAttribute("type", type);
        model.addAttribute("sourceType", sourceType);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("startPage", startPage);
        model.addAttribute("endPage", endPage);
        return "admin/points";
    }

    @GetMapping("/orders")
    public String orders(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        String userIdParam = (userId != null && !userId.isBlank()) ? userId : null;
        String orderNoParam = (orderNo != null && !orderNo.isBlank()) ? orderNo : null;

        OrderType typeParam = null;
        try { if (type != null && !type.isBlank()) typeParam = OrderType.valueOf(type); } catch (Exception ignored) {}

        LocalDateTime startDateParam = null;
        try { if (startDate != null && !startDate.isBlank()) startDateParam = LocalDate.parse(startDate).atStartOfDay(); } catch (Exception ignored) {}

        LocalDateTime endDateParam = null;
        try { if (endDate != null && !endDate.isBlank()) endDateParam = LocalDate.parse(endDate).atTime(23, 59, 59); } catch (Exception ignored) {}

        PageRequest pageable = PageRequest.of(page, PAGE_SIZE);
        Page<Order> orders = orderRepository.searchOrders(userIdParam, orderNoParam, typeParam, startDateParam, endDateParam, pageable);

        int totalPages = orders.getTotalPages();
        int startPage = Math.max(0, page - 2);
        int endPage = Math.min(totalPages - 1, page + 2);

        model.addAttribute("orders", orders);
        model.addAttribute("userId", userId);
        model.addAttribute("orderNo", orderNo);
        model.addAttribute("type", type);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("startPage", startPage);
        model.addAttribute("endPage", endPage);
        return "admin/orders";
    }

    @GetMapping("/order-cancels")
    @ResponseBody
    public ResponseEntity<List<java.util.Map<String, Object>>> orderCancels(
            @RequestParam Long orderId) {

        return orderRepository.findById(orderId).map(order -> {
            List<OrderCancel> cancels = orderCancelRepository.findByOrder(order);
            List<java.util.Map<String, Object>> result = cancels.stream().map(c -> {
                java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("id", c.getId());
                m.put("cancelAmount", c.getCancelAmount());
                m.put("regDateTime", c.getRegDateTime() != null ? c.getRegDateTime().toString() : null);
                return m;
            }).toList();
            return ResponseEntity.ok(result);
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/order-use-details")
    @ResponseBody
    public ResponseEntity<List<java.util.Map<String, Object>>> orderUseDetails(
            @RequestParam Long orderId) {

        return orderRepository.findById(orderId).map(order -> {
            List<PointEvent> events = pointEventRepository.findByOrderAndPointEventTypeOrderByIdDesc(order, PointEventType.USE);
            List<java.util.Map<String, Object>> result = events.stream().map(e -> {
                java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("pointKey", e.getPoint() != null ? e.getPoint().getPointKey() : null);
                m.put("amount", e.getAmount());
                m.put("regDateTime", e.getRegDateTime() != null ? e.getRegDateTime().toString() : null);
                return m;
            }).toList();
            return ResponseEntity.ok(result);
        }).orElse(ResponseEntity.notFound().build());
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
            m.put("cancelAmount", e.getOrderCancel() != null ? e.getOrderCancel().getCancelAmount() : null);
            m.put("regDateTime", e.getRegDateTime() != null ? e.getRegDateTime().toString() : null);
            return m;
        }).toList();

        return ResponseEntity.ok(result);
    }
}
