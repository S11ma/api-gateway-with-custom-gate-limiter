# API Gateway with Custom Rate Limiter

A Spring Cloud Gateway built incrementally, stage by stage, from basic routing
up to a distributed, blacklist-aware, metrics-exposing gateway with four
different rate limiting algorithms.

## Roadmap

- [x] 1. Basic Gateway - route requests to backend services
- [x] 2. JWT Authentication Filter
- [ ] 3. API Key Validation
- [ ] 4. Request Logging
- [ ] 5. In-Memory Fixed Window Rate Limiter
- [ ] 6. Redis-Based Fixed Window (distributed)
- [ ] 7. Sliding Window Rate Limiter
- [ ] 8. Token Bucket (burst traffic support)
- [ ] 9. Blacklist Support
- [ ] 10. Metrics & Monitoring

Each stage is its own commit (or small set of commits) so the git history
shows the design evolving.

## Modules

- `gateway-service` - Spring Cloud Gateway, port 8080. The single entry point.
- `auth-service` - issues JWTs after checking hardcoded credentials, port 8083.
- `order-service` - dummy backend, port 8081.
- `product-service` - dummy backend, port 8082.

## Running everything

Each module is an independent Maven project. Open four terminals:

```bash
cd order-service
mvn spring-boot:run
```

```bash
cd product-service
mvn spring-boot:run
```

```bash
cd auth-service
mvn spring-boot:run
```

```bash
cd gateway-service
mvn spring-boot:run
```

## Testing Stage 1 (routing)

```bash
curl http://localhost:8080/orders
curl http://localhost:8080/orders/5
curl http://localhost:8080/products
curl http://localhost:8080/products/101
```

You should see JSON responses that include `"service": "order-service"` or
`"service": "product-service"` - confirming the request went in through
port 8080 (the gateway) and came back from the correct backend.

You can also inspect the gateway's registered routes directly:

```bash
curl http://localhost:8080/actuator/gateway/routes
```

## Testing Stage 2 (JWT auth)

First, calling a protected route with no token should now fail:

```bash
curl -i http://localhost:8080/orders
# expect: HTTP/1.1 401 Unauthorized
```

Log in through the gateway (auth-service is routed to via `/auth/**`,
same as everything else):

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "seema", "password": "password123"}'
```

This returns a JSON body with a `token`. Copy it, then call a protected
route with it:

```bash
TOKEN="paste-the-token-here"

curl -i http://localhost:8080/orders \
  -H "Authorization: Bearer $TOKEN"
# expect: HTTP/1.1 200 OK, and the order data as before
```

Try a garbage token to confirm rejection works:

```bash
curl -i http://localhost:8080/orders \
  -H "Authorization: Bearer not-a-real-token"
# expect: HTTP/1.1 401 Unauthorized
```

Two demo users exist in `auth-service`'s in-memory `UserStore`:

| username | password    | roles       |
|----------|-------------|-------------|
| seema    | password123 | USER, ADMIN |
| guest    | guestpass   | USER        |

## Why this works this way

**Stage 1:** Spring Cloud Gateway matches incoming requests against
`predicates` (here, path patterns) and forwards them to the `uri` of the
matching route. Right now the URIs are hardcoded to `localhost`, which is
fine for local dev with a handful of known services.

**Stage 2:** `auth-service` and `gateway-service` share one HMAC secret
(`jwt.secret` in both `application.yml` files - they must match exactly).
`auth-service` signs tokens with it on login; `gateway-service`'s
`JwtAuthenticationFilter` (a `GlobalFilter` implementing `Ordered`, running
at order `-1` so it fires before routing) verifies the signature and
expiry on every request, except paths under `/auth/`, which have to stay
open so you can log in in the first place.

On success, the filter doesn't just let the request through - it adds
`X-Auth-Username` and `X-Auth-Roles` headers before forwarding downstream.
That means `order-service` and `product-service` never see a raw JWT and
never need JWT-parsing logic of their own; they just trust headers set by
the gateway. This is the same trust boundary pattern used in most real
gateway setups: authentication is centralized at the edge, backend services
stay simple.

Note this is a symmetric (HS256) setup - one shared secret both signs and
verifies. It's the right choice for a project where the gateway and
auth-service are tightly coupled and deployed together. In a system where
many independently-deployed services needed to verify tokens without ever
being trusted to *issue* them, you'd switch to asymmetric signing (RS256):
auth-service holds a private key and signs, everyone else holds only the
public key and verifies. Worth knowing the distinction even though HS256
is the pragmatic choice here.

## Next stage

Stage 3 adds API key validation as a second, independent access-control
layer alongside JWT auth.
