[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=s11ma_api-gateway-rl&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=s11ma_api-gateway-rl)

# API Gateway with Custom Rate Limiter

A Spring Cloud Gateway, built one deliberate stage at a time — from
routing two requests correctly, up through JWT/API-key auth, blacklisting,
and four different rate limiting algorithms, to a metrics endpoint that
shows exactly what the gateway is doing at any moment.

Nothing here was generated as a finished system. Each stage exists because
the one before it exposed a real gap, and the git history follows that
same order. See `docs/HLD.md` for the full architecture writeup — this
README is the practical "how to run and test it" companion.

## Roadmap — all 10 stages complete

- [x] 1. Basic Gateway
- [x] 2. JWT Authentication Filter
- [x] 3. API Key Validation
- [x] 4. Request Logging
- [x] 9. Blacklist Support *(built ahead of schedule, alongside auth)*
- [x] 5. In-Memory Fixed Window Rate Limiter
- [x] 6. Redis-Based Fixed Window (distributed)
- [x] 7. Sliding Window Rate Limiter
- [x] 8. Token Bucket Implementation
- [x] 10. Metrics & Monitoring

## Modules

| Module | Port | What it does |
|---|---|---|
| `gateway-service` | 8080 | The single entry point — routing, logging, blacklist, auth, rate limiting, metrics |
| `auth-service` | 8083 | Checks credentials, issues signed JWTs |
| `order-service` | 8081 | Dummy backend, returns order data |
| `product-service` | 8082 | Dummy backend, returns product data |

## Prerequisites

- Java 17
- Maven
- Docker (for Redis, used from Stage 6 onward)

## Running everything

Start Redis first, since the gateway's rate limiter (in its `redis`,
`sliding`, or `token-bucket` modes) depends on it:

```bash
docker run --name rl-redis -p 6379:6379 -d redis:7-alpine
docker exec -it rl-redis redis-cli ping   # should print PONG
```

Then open four terminals:

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

## How the pieces fit together, in the order they were built

### 1. Routing works first, with zero Java code

```bash
curl http://localhost:8080/orders
curl http://localhost:8080/products
```

Both should return JSON tagged with `"service": "order-service"` or
`"service": "product-service"` — proof the request went in through 8080
and came back from the right backend. Routing is entirely declarative,
defined in `gateway-service/application.yml` as path-matching rules; no
filter or controller handles it.

### 2 & 3. Getting a token, and calling a protected route

```bash
# Log in through the gateway (auth-service is routed to, same as everything else)
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "seema", "password": "password123"}'

# Copy the returned token, then call a protected route with BOTH headers
TOKEN="paste-token-here"
curl -i http://localhost:8080/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-API-Key: mobile-app-key-123"
```

Try dropping either header one at a time — each should independently
cause a `401`, confirming the two auth layers are genuinely separate
checks, not one covering for the other.

**Demo accounts:** `seema` / `password123` (roles `USER, ADMIN`), `guest`
/ `guestpass` (role `USER`).
**Demo API keys:** `mobile-app-key-123` → client `mobile-app`,
`partner-x-key-456` → client `partner-x`.

### 4. Watching the logs

Every request produces a matched pair of lines in the gateway's console —
`-->` when it arrives, `<--` when a response is on its way out, including
the status code and how long it took. Trigger a `401` (bad token) and a
`200` (valid one) back to back and compare the two log pairs — both get
logged, since the logging filter wraps every other filter, not just the
successful path.

### 9. Blacklisting

Add an entry to `blacklist.ips` or `blacklist.api-keys` in
`gateway-service/application.yml`, restart the gateway, and confirm a
matching request now gets `403` — and gets it *before* the auth filters
even run (the point of putting blacklist first: reject known-bad callers
cheaply, before spending effort validating their credentials).

### 5-8. The four rate limiters

All four live behind one interface and are switched with a single config
value — no code changes needed to swap between them:

```yaml
rate-limit:
  strategy: memory   # memory | redis | sliding | token-bucket
  max-requests: 5
  window-seconds: 60
```

**Fixed window (`memory` or `redis`)** — a straightforward "N requests
per clock-aligned minute" cap. Test it by firing more than the limit
quickly:

```bash
for i in 1 2 3 4 5 6; do
  curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/orders \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-API-Key: mobile-app-key-123"
done
```

Expect five `200`s and a `429`. With `strategy: redis`, you can peek at
the shared counter directly:

```bash
docker exec -it rl-redis redis-cli KEYS "ratelimit:*"
```

And prove it's genuinely distributed by running a second gateway instance
on another port (`mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8090`)
pointed at the same Redis — splitting your 6 test requests across both
ports should still produce exactly one `429`, since both instances share
the same counter.

**Sliding window (`sliding`)** — fixes fixed window's edge-burst problem:
sending a burst right at a window boundary no longer resets to a clean
slate the instant the clock rolls over, since the previous window's count
still weighs into the estimate for a while after. Time a burst near a
minute boundary and compare behavior against `strategy: fixed`/`redis` to
see the difference directly.

**Token bucket (`token-bucket`)** — the odd one out: instead of a hard
per-window cap, a bucket refills continuously and lets a quiet client
burst freely up to its capacity. Fire 5 requests instantly (all succeed,
6th doesn't), then wait about 12 seconds (one token's worth of refill
time, at 5 tokens per 60 seconds) and send exactly one more — it should
succeed, where a window-based limiter would still have you blocked until
the entire window rolled over. That partial recovery is the whole point
of this algorithm.

```bash
docker exec -it rl-redis redis-cli HGETALL "ratelimit:bucket:seema:mobile-app"
```

### 10. Metrics

```bash
curl http://localhost:8080/actuator/gatewaystats
```

Returns every rejection reason and total/forwarded counts in one JSON
response:

```json
{
  "totalRequests": 12,
  "forwardedRequests": 7,
  "rejections": {
    "blacklist": 0,
    "jwt": 2,
    "apiKey": 1,
    "rateLimit": 2
  }
}
```

Send a deliberate mix of good and bad requests, then check this endpoint
— the numbers should match exactly what you sent, which is the actual
test here: not just "does the endpoint respond," but "are the counts
correct."

## Project structure

```
api-gateway-rl/
├── gateway-service/     - the gateway itself: routing, all filters, rate limiters, metrics
├── auth-service/        - issues JWTs
├── order-service/       - dummy backend
├── product-service/     - dummy backend
└── docs/
    └── HLD.md            - full architecture writeup, stage by stage
```

Inside `gateway-service`:

```
src/main/java/com/gateway/gatewayservice/
├── filter/          - the 5 GlobalFilters, in execution order via getOrder()
├── ratelimit/        - RateLimiter interface + 4 implementations
├── config/           - @ConfigurationProperties classes (API keys, blacklist, rate limit)
└── metrics/           - GatewayMetrics + the custom /actuator/gatewaystats endpoint
```

## A note on what's intentionally left out

This project stops short of a few things a production gateway would need
— no persistent user/API-key database (config-backed instead), no
Lua-script atomicity fix for the Redis rate limiters' two-step
increment-then-expire, no TLS, no service discovery. Those aren't
oversights; they're outside what this project set out to demonstrate.
`docs/HLD.md` §8 states them explicitly rather than leaving them implicit.
