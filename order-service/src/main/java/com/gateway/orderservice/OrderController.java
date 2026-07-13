package com.gateway.orderservice;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
public class OrderController {

    @GetMapping("/orders")
    public Map<String, Object> getAllOrders() {
        return Map.of(
                "service", "order-service",
                "timestamp", Instant.now().toString(),
                "orders", List.of(
                        Map.of("id", 1, "item", "Health Insurance Policy", "status", "CONFIRMED"),
                        Map.of("id", 2, "item", "Term Life Policy", "status", "PENDING")
                )
        );
    }

    @GetMapping("/orders/{id}")
    public Map<String, Object> getOrder(@PathVariable int id) {
        return Map.of(
                "service", "order-service",
                "timestamp", Instant.now().toString(),
                "id", id,
                "item", "Sample Order " + id,
                "status", "CONFIRMED"
        );
    }
}
