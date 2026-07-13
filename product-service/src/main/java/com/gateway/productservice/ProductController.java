package com.gateway.productservice;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
public class ProductController {

    @GetMapping("/products")
    public Map<String, Object> getAllProducts() {
        return Map.of(
                "service", "product-service",
                "timestamp", Instant.now().toString(),
                "products", List.of(
                        Map.of("id", 101, "name", "Family Health Shield", "line", "HEALTH"),
                        Map.of("id", 102, "name", "Secure Life Term Plan", "line", "LIFE")
                )
        );
    }

    @GetMapping("/products/{id}")
    public Map<String, Object> getProduct(@PathVariable int id) {
        return Map.of(
                "service", "product-service",
                "timestamp", Instant.now().toString(),
                "id", id,
                "name", "Sample Product " + id
        );
    }
}
