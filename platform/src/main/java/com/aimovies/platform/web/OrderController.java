package com.aimovies.platform.web;

import com.aimovies.platform.model.OrderEntity;
import com.aimovies.platform.model.User;
import com.aimovies.platform.security.AuthSupport;
import com.aimovies.platform.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderService orderService;
    private final AuthSupport auth;

    public OrderController(OrderService orderService, AuthSupport auth) {
        this.orderService = orderService;
        this.auth = auth;
    }

    @PostMapping
    public Map<String, Object> place(@RequestBody Dtos.OrderRequest req, HttpServletRequest request) {
        User user = auth.requireUser(request);
        if (req.projectId() == null) {
            throw ApiException.badRequest("projectId is required");
        }
        OrderEntity order = orderService.placeOrder(user, req.projectId(), req.code());
        return Map.of(
            "order", Views.order(order),
            "points", auth.currentUser(request).getPoints()
        );
    }

    @GetMapping
    public List<Map<String, Object>> myOrders(HttpServletRequest request) {
        User user = auth.requireUser(request);
        return orderService.listForUser(user.getId()).stream()
            .map(Views::order).collect(Collectors.toList());
    }

    @GetMapping("/{orderNo}")
    public Map<String, Object> one(@PathVariable String orderNo, HttpServletRequest request) {
        User user = auth.requireUser(request);
        return Views.order(orderService.getForUser(user.getId(), orderNo));
    }
}
