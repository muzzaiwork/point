package org.musinsa.payments.point.controller;

import lombok.RequiredArgsConstructor;
import org.musinsa.payments.point.domain.Point;
import org.musinsa.payments.point.domain.UserAccount;
import org.musinsa.payments.point.repository.PointRepository;
import org.musinsa.payments.point.repository.UserAccountRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserAccountRepository userAccountRepository;
    private final PointRepository pointRepository;

    @GetMapping
    public String index() {
        return "redirect:/admin/accounts";
    }

    @GetMapping("/accounts")
    public String accounts(Model model) {
        List<UserAccount> accounts = userAccountRepository.findAll();
        model.addAttribute("accounts", accounts);
        return "admin/accounts";
    }

    @GetMapping("/points")
    public String points(
            @RequestParam(required = false) String userId,
            Model model) {
        model.addAttribute("userId", userId);
        if (userId != null && !userId.isBlank()) {
            List<Point> points = pointRepository.findByUserIdOrderByIdDesc(userId);
            model.addAttribute("points", points);
        }
        return "admin/points";
    }
}
