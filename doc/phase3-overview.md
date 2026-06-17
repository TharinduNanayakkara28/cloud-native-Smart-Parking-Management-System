# Phase 3 — Availability Service

## Goal
Answer "which spots are free near me?" in real time, combining PostGIS geospatial queries with Redis-cached state and WebSocket push to connected clients.

## Services in This Phase

| Service | Port | Responsibility |
|---|---|---|
| availability-service | 8084 | Kafka consumer, Redis state cache, PostGIS geo-query, WebSocket push |
| redis | 6379 | Individual spot state store + full-map burst cache |
| postgres-availability | 5434 | PostGIS spatial index for radius queries |

## Architecture

```
Kafka: spot-state topic
      │
      ▼
Availability Service
      ├── update Redis: spot:{id}:state = FREE|OCCUPIED|RESERVED
      ├── evict Redis:  availability:full-map (burst cache)
      └── broadcast WebSocket: /topic/spots → {spotId, state, ...}

GET /spots/available?lat=&lng=&radius=
      │
      ├── PostGIS ST_DWithin → spots within radius
      ├── Redis: filter to FREE only
      └── return AvailableSpotResponse[]

WS  /ws/availability (STOMP + SockJS)
      └── subscribe /topic/spots → real-time spot state changes
```

## Redis Key Schema

| Key | Value | TTL |
|---|---|---|
| `spot:{spotId}:state` | `FREE` / `OCCUPIED` / `RESERVED` | None (evicted on update) |
| `availability:full-map` | JSON: `{spotId → state, ...}` | 5 seconds |

## Running Phase 3

```bash
docker-compose -f infra/docker-compose.yml up --build
```

**REST — find available spots within 500m:**
```bash
TOKEN="<accessToken from /auth/login>"
curl "http://localhost:8080/spots/available?lat=6.92720&lng=79.86175&radius=500" \
  -H "Authorization: Bearer $TOKEN"
```

**WebSocket — real-time updates (JavaScript):**
```js
const socket = new SockJS('http://localhost:8080/ws/availability');
const stomp = Stomp.over(socket);
stomp.connect({}, () => {
  stomp.subscribe('/topic/spots', (msg) => {
    console.log(JSON.parse(msg.body));
  });
});
```

**Then trigger a change:**
```bash
curl -X POST http://localhost:8080/simulate/auto/start
```

## Environment Variables

| Variable | Default | Service |
|---|---|---|
| `REDIS_HOST` | `localhost` | availability-service |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5434/availabilitydb` | availability-service |
| `KAFKA_BROKER` | `localhost:9092` | availability-service |
| `AVAILABILITY_SERVICE_URL` | `http://localhost:8084` | api-gateway |
