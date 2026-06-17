# API Gateway — Implementation Details

## Tech Choice: Spring Cloud Gateway

Spring Cloud Gateway (SCG) runs on Project Reactor (non-blocking). It is the standard Spring approach for reactive API gateways and integrates natively with Spring Boot 3.

## Route Table

| Route ID | Predicate | Filter | Target |
|---|---|---|---|
| `auth-route` | `Path=/auth/**` | none (public) | user-service |
| `user-route` | `Path=/users/**` | `JwtAuthentication` | user-service |

More routes will be added in subsequent phases as services come online.

## Filter Pipeline

```
Incoming request
      │
      ▼
RateLimitFilter (GlobalFilter, order=-1)
  └── > 100 req/IP/window → 429 Too Many Requests
      │
      ▼
Route matching
      │
      ├── /auth/**  → forward to user-service (no auth filter)
      │
      └── /users/** → JwtAuthenticationFilter
                          ├── no/invalid Bearer → 401 Unauthorized
                          └── valid             → inject X-User-Id, X-User-Email headers
                                                  → forward to user-service
```

## JwtAuthenticationFilter

- Extends `AbstractGatewayFilterFactory<Config>` (per-route filter)
- Validates the HS256 access token against the shared `JWT_SECRET`
- On success, mutates the request to add:
  - `X-User-Id: <UUID from sub claim>`
  - `X-User-Email: <email claim>`
- Returns `401` without calling downstream on failure

The user-service also validates the JWT independently (defense in depth). The `X-User-Id` header is available for downstream services that trust the gateway.

## Rate Limiting (Phase 1)

Phase 1 uses an in-memory `ConcurrentHashMap` per-IP counter. This is intentionally simple — no Redis dependency in Phase 1.

**Phase 3 upgrade path**: Replace `RateLimitFilter` with Spring Cloud Gateway's `RequestRateLimiter` filter backed by Redis. The route config change is a one-liner; no other services are affected.

## Adding Routes in Later Phases

Each new service just needs a new entry under `spring.cloud.gateway.routes` in `application.yml`:

```yaml
- id: availability-route
  uri: ${AVAILABILITY_SERVICE_URL:http://localhost:8082}
  predicates:
    - Path=/spots/**
  filters:
    - JwtAuthentication
```

## Health Check

```
GET http://localhost:8080/actuator/health
```

Returns `{"status":"UP"}` when the gateway is running.
