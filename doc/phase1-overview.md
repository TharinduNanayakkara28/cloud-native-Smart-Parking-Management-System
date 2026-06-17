# Phase 1 — User Service + API Gateway

## Goal
Establish the auth foundation: driver registration, login, JWT issuance, vehicle management, and a gateway that routes and protects all traffic.

## Services in This Phase

| Service | Port | Responsibility |
|---|---|---|
| user-service | 8081 | Auth, user profile, vehicle management |
| api-gateway | 8080 | Routing, JWT validation, rate limiting |
| postgres-user | 5432 | Dedicated DB for user-service |

## Architecture

```
Client
  │
  ▼
API Gateway (:8080)
  ├── /auth/**  ──────► User Service (:8081)  [no JWT required]
  └── /users/** ──────► User Service (:8081)  [JWT required]
                                │
                          PostgreSQL (:5432)
                             userdb
```

## JWT Strategy

- **Access token**: HS256 signed, 15-minute TTL, contains `sub` (userId) and `email`
- **Refresh token**: UUID stored in `refresh_tokens` table, 7-day TTL
- **Rotation**: each `/auth/refresh` call deletes the old token and issues a new pair
- The gateway validates access tokens before forwarding protected requests; it also injects `X-User-Id` and `X-User-Email` headers downstream

## Running Phase 1 Locally

```bash
# From project root
docker-compose -f infra/docker-compose.yml up --build
```

Wait for all services to be healthy, then:

```bash
# Register
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Alice","email":"alice@example.com","password":"password123"}'

# Login
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"password123"}'

# Get profile (use accessToken from login response)
curl http://localhost:8080/users/me \
  -H "Authorization: Bearer <accessToken>"
```

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/userdb` | JDBC URL |
| `DB_USERNAME` | `postgres` | DB user |
| `DB_PASSWORD` | `postgres` | DB password |
| `JWT_SECRET` | (insecure default) | Must be ≥ 32 chars in production |
| `USER_SERVICE_URL` | `http://localhost:8081` | Gateway → user-service URL |

## Tests

```bash
cd services/user-service
mvn test
```

Covers:
- `AuthServiceTest` — register (happy + duplicate email), login (happy + wrong password + unknown user)
- `JwtServiceTest` — token generation, parsing, tamper detection
