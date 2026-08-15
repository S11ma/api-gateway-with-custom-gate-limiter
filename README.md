# API Gateway with Custom Rate Limiter

A Spring Cloud Gateway built incrementally, stage by stage, from basic
routing up to a distributed, blacklist-aware, metrics-exposing gateway
with four different rate limiting algorithms.

## Roadmap

- [x] 1. Basic Gateway - route requests to backend services
- [x] 2. JWT Authentication Filter
- [x] 3. API Key Validation
- [x] 4. Request Logging
- [x] 9. Blacklist Support *(built ahead of schedule, alongside auth)*
- [x] 5. In-Memory Fixed Window Rate Limiter
- [ ] 6. Redis-Based Fixed Window (distributed)
- [ ] 7. Sliding Window Rate Limiter
- [ ] 8. Token Bucket Implementation
- [ ] 10. Metrics & Monitoring

See `docs/HLD.md` for the full architecture writeup and diagrams.

## Modules

- `gateway-service` - Spring Cloud Gateway, port 8080. The single entry point.
- `auth-service` - issues JWTs after checking hardcoded credentials, port 8083.
- `order-service` - dummy backend, port 8081.
- `product-service` - dummy backend, port 8082.

## Gateway filter chain (in order)

1. **LoggingFilter** (order `-3`) - wraps everything, logs every request in and every response out.
2. **BlacklistFilter** (order `-2`) - rejects blacklisted IPs/API keys with `403`, before any auth work happens.
3. **JwtAuthenticationFilter** (order `-1`) - validates the `Authorization: Bearer` token, `401` if missing/invalid.
4. **ApiKeyAuthenticationFilter** (order `0`) - validates `X-API-Key`, `401` if missing/invalid.
5. **RateLimitingFilter** (order `1`) - enforces the fixed window limit per `username:client`, `429` if exceeded.
6. Route forwarding to the matched backend service.

## Running everything

Each module is an independent Maven project. Open four terminals:

```bash
cd order-service && mvn spring-boot:run
```
```bash
cd product-service && mvn spring-boot:run
```
```bash
cd auth-service && mvn spring-boot:run
```
```bash
cd gateway-service && mvn spring-boot:run
```

## Testing routing (Stage 1)

```bash
curl http://localhost:8080/orders
curl http://localhost:8080/products
```

## Testing JWT + API key auth (Stages 2-3)

```bash
# 1. Log in to get a token
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "seema", "password": "password123"}'

# 2. Call a protected route with both headers
TOKEN="paste-token-here"
curl -i http://localhost:8080/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-API-Key: mobile-app-key-123"
```

Demo users: `seema` / `password123` (roles USER, ADMIN), `guest` / `guestpass` (role USER).
Demo API keys: `mobile-app-key-123` (client `mobile-app`), `partner-x-key-456` (client `partner-x`).

## Testing the blacklist (Stage 9)

Add a value to `blacklist.ips` or `blacklist.api-keys` in
`gateway-service/application.yml`, restart the gateway, then confirm a
matching request gets `403` instead of proceeding to auth checks at all.

## Testing the rate limiter (Stage 5)

The default limit is deliberately low for easy testing: **5 requests per
60-second window**, per `username:client` combination.

```bash
# Fire more than 5 requests quickly with the same token + API key
for i in 1 2 3 4 5 6; do
  curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/orders \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-API-Key: mobile-app-key-123"
done
```

Expect `200` for the first 5, then `429` for the 6th. Wait until the next
60-second window rolls over (aligned to the clock, not to when you started
sending requests) and the count resets.

Try it again with a *different* API key (`partner-x-key-456`) using the
same JWT - it should get its own fresh count of 5, since the key is
`username:client`, not just `username`. This is what confirms the
composite key is actually working, not just a single global counter.

## Why this works this way

**Routing (Stage 1):** Spring Cloud Gateway matches requests against
`predicates` (path patterns) and forwards to the matching route's `uri`.

**Auth (Stages 2-3):** `auth-service` and `gateway-service` share one HMAC
secret for JWT signing/verification. On success, the JWT filter adds
`X-Auth-Username`/`X-Auth-Roles` headers; the API key filter adds
`X-Client-Name`. Backend services never see raw tokens or keys - they
trust headers the gateway already resolved.

**Blacklist (Stage 9):** runs before any auth work, on purpose - it's a
cheap check that should reject known-bad callers before spending effort
validating their (possibly still technically-valid) credentials. Uses
`403` rather than `401`, since the caller isn't missing credentials, they're
explicitly denied.

**Rate limiting (Stage 5):** the `FixedWindowRateLimiter` keeps one
counter per key in a `ConcurrentHashMap`, each tracking its own window-start
timestamp. Windows are clock-aligned (e.g. with a 60s window: `12:00:00-12:00:59`),
not "60 seconds from this key's first request" - which is exactly what
creates the edge-burst problem Stage 7 (sliding window) fixes later. This
implementation only works correctly for a single gateway instance - all
its state lives in that one JVM's memory. Stage 6 replaces the in-memory
map with Redis specifically to fix that, once you're running more than
one gateway instance.

## Next stage

Stage 6 moves the rate limiter's counter storage from the in-memory map
into Redis, so the limit holds correctly across multiple gateway instances.
