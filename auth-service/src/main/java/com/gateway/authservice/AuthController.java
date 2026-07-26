package com.gateway.authservice;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class AuthController {

    private final UserStore userStore;
    private final JwtUtil jwtUtil;

    public AuthController(UserStore userStore, JwtUtil jwtUtil) {
        this.userStore = userStore;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request) {
        if (!userStore.isValid(request.getUsername(), request.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid username or password"));
        }

        List<String> roles = userStore.rolesOf(request.getUsername());
        String token = jwtUtil.generateToken(request.getUsername(), roles);

        return ResponseEntity.ok(Map.of(
                "token", token,
                "username", request.getUsername(),
                "roles", roles
        ));
    }
}
