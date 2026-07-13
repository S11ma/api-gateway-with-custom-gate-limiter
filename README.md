# API Gateway with Custom Rate Limiter

A Spring Cloud Gateway built incrementally, stage by stage, from basic routing
up to a distributed, blacklist-aware, metrics-exposing gateway with four
different rate limiting algorithms.

## Roadmap

- [x] 1. Basic Gateway - route requests to backend services
- [ ] 2. JWT Authentication Filter
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

## Modules (Stage 1)

- `gateway-service` - Spring Cloud Gateway, port 8080. The single entry point.
- `order-service` - dummy backend, port 8081.
- `product-service` - dummy backend, port 8082.

## Running Stage 1

Each module is an independent Maven project. Open three terminals:

```bash
cd order-service
mvn spring-boot:run
```

```bash
cd product-service
mvn spring-boot:run
```

```bash
cd gateway-service
mvn spring-boot:run
```

Then test the gateway is routing correctly:

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

## Why this works this way

Spring Cloud Gateway matches incoming requests against `predicates`
(here, path patterns) and forwards them to the `uri` of the matching route.
Right now the URIs are hardcoded to `localhost`, which is fine for local dev
with two known services. As this project grows, this is the layer everything
else attaches to: auth filters, rate limiter filters, and logging filters all
plug into the same gateway request/response lifecycle.

## Next stage

Stage 2 adds a JWT authentication filter in front of these routes.
