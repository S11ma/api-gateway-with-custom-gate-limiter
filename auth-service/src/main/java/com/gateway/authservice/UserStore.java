package com.gateway.authservice;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class UserStore {

    private record UserRecord(String password, List<String> roles) {
    }

    private final Map<String, UserRecord> users = Map.of(
            "seema", new UserRecord("password123", List.of("USER", "ADMIN")),
            "guest", new UserRecord("guestpass", List.of("USER"))
    );

    public boolean isValid(String username, String password) {
        UserRecord record = users.get(username);
        return record != null && record.password().equals(password);
    }

    public List<String> rolesOf(String username) {
        UserRecord record = users.get(username);
        return record == null ? List.of() : record.roles();
    }
}
