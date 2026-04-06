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
            @RequestParam(defaultValue = "10") int size,
            Model model) {

        int pageSize = (size == 10 || size == 20 || size == 50 || size == 100) ? size : 10;
        PageRequest pageable = PageRequest.of(page, pageSize, Sort.by("regDateTime").descending());
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
        model.addAttribute("pageSize", pageSize);
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
            @RequestParam(required = false) String pointKey,
            @RequestParam(required = false) String rootPointKey,
            @RequestParam(required = false) String orderNo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
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

        String pointKeyParam = (pointKey != null && !pointKey.isBlank()) ? pointKey : null;
        String rootPointKeyParam = (rootPointKey != null && !rootPointKey.isBlank()) ? rootPointKey : null;

        // orderNo 입력 시 해당 주문의 사용/취소와 연관된 pointKey 목록 추출
        java.util.Set<String> pointKeySetParam = null;
        if (orderNo != null && !orderNo.isBlank()) {
            java.util.Optional<org.musinsa.payments.point.domain.Order> orderOpt = orderRepository.findByOrderNo(orderNo.trim());
            if (orderOpt.isPresent()) {
                pointKeySetParam = pointEventRepository.findByOrder(orderOpt.get()).stream()
                        .map(pe -> pe.getPoint().getPointKey())
                        .collect(java.util.stream.Collectors.toSet());
            } else {
                pointKeySetParam = java.util.Collections.emptySet();
            }
        }

        int pageSize = (size == 10 || size == 20 || size == 50 || size == 100) ? size : 10;
        PageRequest pageable = PageRequest.of(page, pageSize);
        Page<Point> points = pointRepository.searchPoints(userIdParam, cancelledParam, typeParam, sourceTypeParam, startDateParam, endDateParam, pointKeyParam, rootPointKeyParam, pointKeySetParam, pageable);

        Map<Long, String> restoredPointKeyMap = new HashMap<>();
        for (Point p : points) {
            List<Point> restoredList = pointRepository.findByOriginPointKey(p.getPointKey());
            if (!restoredList.isEmpty()) {
                restoredPointKeyMap.put(p.getId(), restoredList.get(0).getPointKey());
            }
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
        model.addAttribute("pointKey", pointKey);
        model.addAttribute("rootPointKey", rootPointKey);
        model.addAttribute("orderNo", orderNo);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("startPage", startPage);
        model.addAttribute("endPage", endPage);
        model.addAttribute("pageSize", pageSize);
        return "admin/points";
    }

    @GetMapping("/stats")
    public String stats(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String startMonth,
            @RequestParam(required = false) String endMonth,
            @RequestParam(required = false) String startYear,
            @RequestParam(required = false) String endYear,
            @RequestParam(defaultValue = "daily") String unit,
            Model model) {

        LocalDate startDateParam;
        LocalDate endDateParam;

        if ("yearly".equals(unit)) {
            int sy = (startYear != null && !startYear.isBlank()) ? Integer.parseInt(startYear) : 2025;
            int ey = (endYear != null && !endYear.isBlank()) ? Integer.parseInt(endYear) : 2025;
            startDateParam = LocalDate.of(sy, 1, 1);
            endDateParam = LocalDate.of(ey, 12, 31);
            startYear = String.valueOf(sy);
            endYear = String.valueOf(ey);
        } else if ("monthly".equals(unit)) {
            java.time.YearMonth defaultStart = java.time.YearMonth.of(2025, 1);
            java.time.YearMonth defaultEnd = java.time.YearMonth.of(2025, 12);
            java.time.YearMonth smYm = (startMonth != null && !startMonth.isBlank()) ? java.time.YearMonth.parse(startMonth) : defaultStart;
            java.time.YearMonth emYm = (endMonth != null && !endMonth.isBlank()) ? java.time.YearMonth.parse(endMonth) : defaultEnd;
            startDateParam = smYm.atDay(1);
            endDateParam = emYm.atEndOfMonth();
            startMonth = smYm.toString();
            endMonth = emYm.toString();
        } else {
            startDateParam = (startDate != null && !startDate.isBlank())
                    ? LocalDate.parse(startDate) : LocalDate.of(2025, 1, 1);
            endDateParam = (endDate != null && !endDate.isBlank())
                    ? LocalDate.parse(endDate) : LocalDate.of(2025, 12, 31);
            startDate = startDateParam.toString();
            endDate = endDateParam.toString();
        }

        List<Map<String, Object>> statsList = new java.util.ArrayList<>();

        if ("yearly".equals(unit)) {
            List<Object[]> rows = pointEventRepository.findYearlyAggregation(startDateParam, endDateParam);
            java.util.TreeMap<String, Map<String, Long>> statsMap = new java.util.TreeMap<>();
            for (Object[] row : rows) {
                String period = String.valueOf(row[0]);
                PointEventType eventType = (PointEventType) row[1];
                Long amount = (Long) row[2];
                statsMap.computeIfAbsent(period, k -> new HashMap<>()).put(eventType.name(), amount);
            }
            // 연도 범위 내 모든 연도 포함
            int syInt = startDateParam.getYear();
            int eyInt = endDateParam.getYear();
            for (int y = syInt; y <= eyInt; y++) {
                String period = String.valueOf(y);
                Map<String, Long> m = statsMap.getOrDefault(period, new HashMap<>());
                Map<String, Object> entry = new java.util.LinkedHashMap<>();
                entry.put("date", period);
                long acc = m.getOrDefault("ACCUMULATE", 0L);
                long accCan = m.getOrDefault("ACCUMULATE_CANCEL", 0L);
                long use = m.getOrDefault("USE", 0L);
                long useCan = m.getOrDefault("USE_CANCEL", 0L);
                long exp = m.getOrDefault("EXPIRE", 0L);
                long restore = m.getOrDefault("EXPIRED_CANCEL_RESTORE", 0L);
                entry.put("ACCUMULATE", acc);
                entry.put("ACCUMULATE_CANCEL", accCan);
                entry.put("USE", use);
                entry.put("USE_CANCEL", useCan);
                entry.put("EXPIRE", exp);
                entry.put("EXPIRED_CANCEL_RESTORE", restore);
                entry.put("SUM", acc - accCan - use + useCan - exp + restore);
                statsList.add(entry);
            }
        } else if ("monthly".equals(unit)) {
            List<Object[]> rows = pointEventRepository.findMonthlyAggregation(startDateParam, endDateParam);
            java.util.TreeMap<String, Map<String, Long>> statsMap = new java.util.TreeMap<>();
            for (Object[] row : rows) {
                String period = (String) row[0];
                PointEventType eventType = (PointEventType) row[1];
                Long amount = (Long) row[2];
                statsMap.computeIfAbsent(period, k -> new HashMap<>()).put(eventType.name(), amount);
            }
            // 월 범위 내 모든 월 포함
            java.time.YearMonth startYm = java.time.YearMonth.from(startDateParam);
            java.time.YearMonth endYm = java.time.YearMonth.from(endDateParam);
            for (java.time.YearMonth ym = startYm; !ym.isAfter(endYm); ym = ym.plusMonths(1)) {
                String period = ym.toString();
                Map<String, Long> m = statsMap.getOrDefault(period, new HashMap<>());
                Map<String, Object> entry = new java.util.LinkedHashMap<>();
                entry.put("date", period);
                long acc = m.getOrDefault("ACCUMULATE", 0L);
                long accCan = m.getOrDefault("ACCUMULATE_CANCEL", 0L);
                long use = m.getOrDefault("USE", 0L);
                long useCan = m.getOrDefault("USE_CANCEL", 0L);
                long exp = m.getOrDefault("EXPIRE", 0L);
                long restore = m.getOrDefault("EXPIRED_CANCEL_RESTORE", 0L);
                entry.put("ACCUMULATE", acc);
                entry.put("ACCUMULATE_CANCEL", accCan);
                entry.put("USE", use);
                entry.put("USE_CANCEL", useCan);
                entry.put("EXPIRE", exp);
                entry.put("EXPIRED_CANCEL_RESTORE", restore);
                entry.put("SUM", acc - accCan - use + useCan - exp + restore);
                statsList.add(entry);
            }
        } else {
            List<Object[]> rows = pointEventRepository.findDailyAggregation(startDateParam, endDateParam);
            java.util.TreeMap<LocalDate, Map<String, Long>> statsMap = new java.util.TreeMap<>();
            for (Object[] row : rows) {
                LocalDate date = (LocalDate) row[0];
                PointEventType eventType = (PointEventType) row[1];
                Long amount = (Long) row[2];
                statsMap.computeIfAbsent(date, k -> new HashMap<>()).put(eventType.name(), amount);
            }
            LocalDate cursor = startDateParam;
            while (!cursor.isAfter(endDateParam)) {
                Map<String, Long> dayMap = statsMap.getOrDefault(cursor, new HashMap<>());
                Map<String, Object> entry = new java.util.LinkedHashMap<>();
                entry.put("date", cursor.toString());
                long acc = dayMap.getOrDefault("ACCUMULATE", 0L);
                long accCan = dayMap.getOrDefault("ACCUMULATE_CANCEL", 0L);
                long use = dayMap.getOrDefault("USE", 0L);
                long useCan = dayMap.getOrDefault("USE_CANCEL", 0L);
                long exp = dayMap.getOrDefault("EXPIRE", 0L);
                long restore = dayMap.getOrDefault("EXPIRED_CANCEL_RESTORE", 0L);
                entry.put("ACCUMULATE", acc);
                entry.put("ACCUMULATE_CANCEL", accCan);
                entry.put("USE", use);
                entry.put("USE_CANCEL", useCan);
                entry.put("EXPIRE", exp);
                entry.put("EXPIRED_CANCEL_RESTORE", restore);
                entry.put("SUM", acc - accCan - use + useCan - exp + restore);
                statsList.add(entry);
                cursor = cursor.plusDays(1);
            }
        }

        Map<String, Long> totals = new HashMap<>();
        totals.put("ACCUMULATE", statsList.stream().mapToLong(r -> (Long) r.get("ACCUMULATE")).sum());
        totals.put("ACCUMULATE_CANCEL", statsList.stream().mapToLong(r -> (Long) r.get("ACCUMULATE_CANCEL")).sum());
        totals.put("USE", statsList.stream().mapToLong(r -> (Long) r.get("USE")).sum());
        totals.put("USE_CANCEL", statsList.stream().mapToLong(r -> (Long) r.get("USE_CANCEL")).sum());
        totals.put("EXPIRE", statsList.stream().mapToLong(r -> (Long) r.get("EXPIRE")).sum());
        totals.put("EXPIRED_CANCEL_RESTORE", statsList.stream().mapToLong(r -> (Long) r.get("EXPIRED_CANCEL_RESTORE")).sum());
        totals.put("SUM", statsList.stream().mapToLong(r -> (Long) r.get("SUM")).sum());

        model.addAttribute("statsList", statsList);
        model.addAttribute("totals", totals);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("startMonth", startMonth);
        model.addAttribute("endMonth", endMonth);
        model.addAttribute("startYear", startYear);
        model.addAttribute("endYear", endYear);
        model.addAttribute("unit", unit);
        return "admin/stats";
    }

    @GetMapping("/orders")
    public String orders(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {

        String userIdParam = (userId != null && !userId.isBlank()) ? userId : null;
        String orderNoParam = (orderNo != null && !orderNo.isBlank()) ? orderNo : null;

        OrderType typeParam = null;
        try { if (type != null && !type.isBlank()) typeParam = OrderType.valueOf(type); } catch (Exception ignored) {}

        LocalDateTime startDateParam = null;
        try { if (startDate != null && !startDate.isBlank()) startDateParam = LocalDate.parse(startDate).atStartOfDay(); } catch (Exception ignored) {}

        LocalDateTime endDateParam = null;
        try { if (endDate != null && !endDate.isBlank()) endDateParam = LocalDate.parse(endDate).atTime(23, 59, 59); } catch (Exception ignored) {}

        int pageSize = (size == 10 || size == 20 || size == 50 || size == 100) ? size : 10;
        PageRequest pageable = PageRequest.of(page, pageSize);
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
        model.addAttribute("pageSize", pageSize);
        return "admin/orders";
    }

    @GetMapping("/order-cancels")
    @ResponseBody
    public ResponseEntity<java.util.Map<String, Object>> orderCancels(
            @RequestParam Long orderId) {

        return orderRepository.findById(orderId).map(order -> {
            // 취소 이력 목록
            List<OrderCancel> cancels = orderCancelRepository.findByOrder(order);
            List<java.util.Map<String, Object>> cancelList = cancels.stream().map(c -> {
                java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("id", c.getId());
                m.put("cancelAmount", c.getCancelAmount());
                m.put("regDateTime", c.getRegDateTime() != null ? c.getRegDateTime().toString() : null);
                return m;
            }).toList();

            // 포인트별 취소 상세 (USE_CANCEL + AUTO_RESTORED)
            List<PointEvent> useCancelEvents = pointEventRepository.findByOrderAndPointEventTypeOrderByIdDesc(order, PointEventType.USE_CANCEL);
            // EXPIRED_CANCEL_RESTORE 이벤트는 order가 null이고 orderCancel로만 연결되므로 orderCancel을 통해 조회
            List<PointEvent> autoRestoredEvents = cancels.stream()
                    .flatMap(c -> pointEventRepository.findByOrderCancelAndPointEventType(c, PointEventType.EXPIRED_CANCEL_RESTORE).stream())
                    .toList();

            List<java.util.Map<String, Object>> pointDetails = new java.util.ArrayList<>();
            for (PointEvent e : useCancelEvents) {
                java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("cancelId", e.getOrderCancel() != null ? e.getOrderCancel().getId() : null);
                m.put("eventType", "USE_CANCEL");
                m.put("eventTypeLabel", "사용취소");
                m.put("pointKey", e.getPoint() != null ? e.getPoint().getPointKey() : null);
                m.put("amount", e.getAmount());
                m.put("regDateTime", e.getRegDateTime() != null ? e.getRegDateTime().toString() : null);
                pointDetails.add(m);
            }
            for (PointEvent e : autoRestoredEvents) {
                java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("cancelId", e.getOrderCancel() != null ? e.getOrderCancel().getId() : null);
                m.put("eventType", "AUTO_RESTORED");
                m.put("eventTypeLabel", "만료 후 재지급");
                m.put("pointKey", e.getPoint() != null ? e.getPoint().getPointKey() : null);
                m.put("expiredPointKey", e.getPoint() != null ? e.getPoint().getOriginPointKey() : null);
                m.put("amount", e.getAmount());
                m.put("regDateTime", e.getRegDateTime() != null ? e.getRegDateTime().toString() : null);
                pointDetails.add(m);
            }
            pointDetails.sort((a, b) -> {
                String da = (String) a.get("regDateTime");
                String db = (String) b.get("regDateTime");
                if (da == null) return 1;
                if (db == null) return -1;
                return da.compareTo(db);
            });

            java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("cancels", cancelList);
            result.put("pointDetails", pointDetails);
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
            m.put("orderId", e.getOrder() != null ? e.getOrder().getId() : null);
            m.put("orderNo", e.getOrder() != null ? e.getOrder().getOrderNo() : null);
            m.put("orderCancelId", e.getOrderCancel() != null ? e.getOrderCancel().getId() : null);
            m.put("cancelAmount", e.getOrderCancel() != null ? e.getOrderCancel().getCancelAmount() : null);
            m.put("regDateTime", e.getRegDateTime() != null ? e.getRegDateTime().toString() : null);
            return m;
        }).toList();

        return ResponseEntity.ok(result);
    }

    @GetMapping("/points-by-root-key")
    @ResponseBody
    public ResponseEntity<List<java.util.Map<String, Object>>> pointsByRootKey(
            @RequestParam String rootPointKey) {

        List<Point> points = pointRepository.findByRootPointKey(rootPointKey);

        List<java.util.Map<String, Object>> result = points.stream().map(p -> {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("pointKey", p.getPointKey());
            m.put("userId", p.getUserId());
            m.put("type", p.getType().name());
            m.put("typeLabel", p.getType().getDescription());
            m.put("pointSourceType", p.getPointSourceType().name());
            m.put("pointSourceTypeLabel", p.getPointSourceType().getDescription());
            m.put("accumulatedPoint", p.getAccumulatedPoint());
            m.put("remainingPoint", p.getRemainingPoint());
            m.put("usedPoint", p.getUsedPoint());
            m.put("expiredPoint", p.getExpiredPoint());
            m.put("isCancelled", p.isCancelled());
            m.put("isExpired", p.isExpired());
            m.put("originPointKey", p.getOriginPointKey());
            m.put("rootPointKey", p.getRootPointKey());
            m.put("expiryDate", p.getExpiryDate() != null ? p.getExpiryDate().toString() : null);
            m.put("regDateTime", p.getRegDateTime() != null ? p.getRegDateTime().toString() : null);
            return m;
        }).toList();

        return ResponseEntity.ok(result);
    }
}
