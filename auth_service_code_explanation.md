# Comprehensive Code & Java Fundamentals Guide: `auth-service`

This document provides a detailed breakdown of the `auth-service` structure, logic, and core Java concepts used in its implementation.

---

## 1. Project Directory Structure

```text
auth-service/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/
    │   │   └── com/
    │   │       └── gateway/
    │   │           └── authservice/
    │   │               ├── AuthServiceApplication.java   # App Entrypoint
    │   │               ├── AuthController.java           # REST Endpoints
    │   │               ├── JwtUtil.java                  # Cryptographic Token Helper
    │   │               ├── LoginRequest.java             # Request DTO
    │   │               └── UserStore.java                # In-memory database store
    │   └── resources/
    │       └── application.yml                           # Configuration
```

---

## 2. Core Java Concepts Deep-Dive

Before explaining the code line-by-line, here is a detailed breakdown of the fundamental Java features utilized in this service:

### A. Java `record`
Introduced as a standard feature in **Java 16**, a `record` is a special kind of class designed to act as an **immutable data carrier**.
* **Traditional Class**: Requires you to write boilerplate code: `private final` fields, constructors, getter methods, `toString()`, `equals()`, and `hashCode()`.
* **Record**: Java automatically generates all of this behind the scenes.
* **Accessor Method Naming**: Unlike standard classes that use the `get` prefix (e.g. `getPassword()`), records generate accessor methods matching the field name exactly (e.g. `password()` and `roles()`).

### B. Java `Map` & `Map.of()`
* **`Map<String, UserRecord>`**: An interface representing a collection of key-value pairs. 
  * The key type is `String` (username).
  * The value type is `UserRecord` (password and roles).
* **`Map.of()`**: Introduced in **Java 9**, this is a static factory method used to create an **immutable** (unmodifiable) map. 
  * Attempting to modify this map (e.g., adding or removing entries) will throw an `UnsupportedOperationException`.
  * It is highly optimized, thread-safe, and does not allow `null` keys or values.

### C. Member Access Operator (The Dot `.`)
In Java, the dot operator (`.`) is used to access instance variables, records, or methods on an object.
* `record.roles()` means: "Look at the object referred to by the reference variable `record`, and invoke the instance method `roles()` on it."

### D. Object Value Equality: `.equals()` vs `==`
In Java, there is a major difference between testing equality with the `==` operator and the `.equals()` method:
1. **Reference Equality (`==`)**: Checks if two references point to the **exact same memory location**.
2. **Value Equality (`.equals()`)**: Checks if two objects contain the **same data (value)**, regardless of where they are stored in memory.
* **Why it is used here**: Strings are objects. If you write `password == "password123"`, it might return `false` even if the characters are identical, because they might be stored in different parts of memory. Using `.equals()` ensures the actual character sequence is compared.
* **Alternatives & Nuances**:
  * `Objects.equals(a, b)`: A helper utility that is null-safe. Calling `a.equals(b)` will crash with a `NullPointerException` if `a` is `null`. `Objects.equals(a, b)` returns `false` safely instead.
  * `equalsIgnoreCase()`: Checks character sequence matching while ignoring case differences (e.g., `"Seema"` matches `"seema"`). This is suitable for usernames, but should **never** be used for passwords, which are case-sensitive.

---

## 3. Detailed Line-by-Line Code Breakdown

---

### File: [UserStore.java](file:///c:/Users/bhaba/Downloads/api-gateway-rl-stage1/api-gateway-rl/auth-service/src/main/java/com/gateway/authservice/UserStore.java)
Provides the data storage logic in-memory.

```java
package com.gateway.authservice;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

// @Component registers this class as a Spring Bean, making it eligible for dependency injection.
@Component
public class UserStore {

    // Declares an immutable data carrier record representing a user's password and roles.
    // Java automatically generates getters (password() and roles()), constructor, equals/hashCode.
    private record UserRecord(String password, List<String> roles) {
    }

    // Creates an immutable Map storing username keys mapped to their respective UserRecords.
    // Map.of() creates an unmodifiable, highly optimized map.
    private final Map<String, UserRecord> users = Map.of(
            "seema", new UserRecord("password123", List.of("USER", "ADMIN")),
            "guest", new UserRecord("guestpass", List.of("USER"))
    );

    // Validates credentials.
    public boolean isValid(String username, String password) {
        // Looks up the UserRecord from the map using the username.
        UserRecord record = users.get(username);
        
        // Checks if the user exists (record != null) AND if the password matches.
        // We use .equals() because comparing strings with '==' compares memory addresses, not content.
        return record != null && record.password().equals(password);
    }

    // Retrieves user roles safely.
    public List<String> rolesOf(String username) {
        // Looks up the user record.
        UserRecord record = users.get(username);
        
        // Ternary operator checks if user exists.
        // If record is null, returns an empty list.
        // If not null, calls record.roles() using the dot operator to access the record's getter.
        return record == null ? List.of() : record.roles();
    }
}
```

---

### File: [JwtUtil.java](file:///c:/Users/bhaba/Downloads/api-gateway-rl-stage1/api-gateway-rl/auth-service/src/main/java/com/gateway/authservice/JwtUtil.java)
Handles cryptographic keys and JWT generation.

#### Detailed Code Explanation & Patterns Used:
* **Base64 Decoding**: Standard cryptographic keys consist of raw byte structures that contain non-printable characters. To store keys as text (e.g., in configuration files), they are encoded into Base64 format. `Decoders.BASE64.decode(secret)` decodes the text string back into raw bytes (`byte[]`).
* **The Builder Design Pattern**: JJWT uses the builder pattern to construct tokens. Instead of passing many parameters into a constructor, the builder lets you set variables step-by-step via chained method calls (e.g. `.setSubject().claim()`) and finally compiles the object using `.compact()`.

```java
package com.gateway.authservice;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.List;

// Registers this utility class as a Spring Bean component.
@Component
public class JwtUtil {

    // Injects the Base64-encoded secret key from application.yml.
    @Value("${jwt.secret}")
    private String secret;

    // Injects the expiration time duration (3,600,000 milliseconds = 1 hour) from application.yml.
    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    // Prepares the cryptographic signature key.
    private Key signingKey() {
        // Decodes the configuration's Base64 text string into a raw byte array.
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        
        // Converts the raw byte array into a HMAC cryptographic Key object.
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // Constructs and signs the JWT.
    public String generateToken(String username, List<String> roles) {
        // Gets the current system timestamp.
        Date now = new Date();
        
        // Calculates the future expiration date by adding expirationMs to current time.
        Date expiry = new Date(now.getTime() + expirationMs);

        // Uses the Builder Pattern to construct the token.
        return Jwts.builder()
                // Set the Standard 'sub' (subject) claim to the username.
                .setSubject(username)
                
                // Add a custom claim key 'roles' carrying the list of user roles.
                .claim("roles", roles)
                
                // Set 'iat' (Issued At) claim to represent token creation time.
                .setIssuedAt(now)
                
                // Set 'exp' (Expiration Time) claim after which the token is invalid.
                .setExpiration(expiry)
                
                // Cryptographically signs the payload with the HMAC-SHA256 algorithm and signature key.
                .signWith(signingKey(), SignatureAlgorithm.HS256)
                
                // Serializes the JWT into the final string structure (header.payload.signature).
                .compact();
    }
}
```

---

### File: [AuthController.java](file:///c:/Users/bhaba/Downloads/api-gateway-rl-stage1/api-gateway-rl/auth-service/src/main/java/com/gateway/authservice/AuthController.java)
Exposes the REST API endpoint mapping `/auth/login`.

```java
package com.gateway.authservice;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

// Declares that this class is an HTTP controller. Returning values write JSON directly into response body.
@RestController
public class AuthController {

    // Local variables holding dependencies. 'final' makes them immutable.
    private final UserStore userStore;
    private final JwtUtil jwtUtil;

    // Constructor injection: Spring Boot finds the beans for UserStore and JwtUtil and injects them here.
    public AuthController(UserStore userStore, JwtUtil jwtUtil) {
        this.userStore = userStore;
        this.jwtUtil = jwtUtil;
    }

    // Binds HTTP POST requests hitting "/auth/login" to this method.
    // ResponseEntity allows custom HTTP status codes (like 200, 401) to be returned.
    @PostMapping("/auth/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request) {
        // Validates credentials using the UserStore bean.
        // @RequestBody tells Jackson to deserialize the incoming JSON payload into LoginRequest object.
        if (!userStore.isValid(request.getUsername(), request.getPassword())) {
            // If invalid, returns HTTP 401 Unauthorized status with error detail.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid username or password"));
        }

        // If valid, fetches user roles from UserStore.
        List<String> roles = userStore.rolesOf(request.getUsername());
        
        // Generates the cryptographic JWT token.
        String token = jwtUtil.generateToken(request.getUsername(), roles);

        // Returns HTTP 200 OK along with token, username, and assigned roles.
        return ResponseEntity.ok(Map.of(
                "token", token,
                "username", request.getUsername(),
                "roles", roles
        ));
    }
}
```

---

### File: [LoginRequest.java](file:///c:/Users/bhaba/Downloads/api-gateway-rl-stage1/api-gateway-rl/auth-service/src/main/java/com/gateway/authservice/LoginRequest.java)
Standard JavaBean/DTO (Data Transfer Object) mapping incoming JSON requests.

```java
package com.gateway.authservice;

public class LoginRequest {
    // Private variables representing fields inside the HTTP JSON body.
    private String username;
    private String password;

    // No-argument constructor required by deserializers (like Jackson) to initialize empty objects.
    public LoginRequest() {
    }

    // Getters and Setters used to access or modify properties.
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
```
