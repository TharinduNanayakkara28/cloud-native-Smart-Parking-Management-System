# Phase 9 — Monitoring + Deployment

## Goal
Make the system production-observable: every service exposes a `/actuator/prometheus` metrics endpoint, Prometheus scrapes them on a 15-second interval, and Grafana provides a pre-built dashboard covering service health, HTTP throughput, JVM memory, and Kafka consumer lag. A root `README.md` documents the full project for recruiters and reviewers.

## What Changes in This Phase

| Area | Change |
|---|---|
| All 10 services | Add `micrometer-registry-prometheus` dependency; expose `prometheus` actuator endpoint |
| `infra/prometheus/` | New — Prometheus config with scrape targets for every service |
| `infra/grafana/` | New — Datasource + dashboard provider + Smart Parking dashboard JSON |
| `infra/docker-compose.yml` | Add `prometheus` (port 9090) and `grafana` (port 3000) |
| `README.md` | New — full project documentation at repo root |

## Dashboard Panels

| Panel | Metric | Description |
|---|---|---|
| Service Health | `up{job=~"..."}` | Green/red per service |
| HTTP Request Rate | `rate(http_server_requests_seconds_count[5m])` | Requests/second per service |
| HTTP Error Rate | `rate(http_server_requests_seconds_count{status=~"5.."}[5m])` | 5xx errors per service |
| HTTP P99 Latency | `histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[5m]))` | 99th percentile response time |
| JVM Heap Used | `jvm_memory_used_bytes{area="heap"}` | Memory pressure per service |
| Kafka Consumer Lag | `kafka_consumer_fetch_manager_records_lag` | How far behind each consumer group is |
| DB Connection Pool | `hikaricp_connections_active` | Active DB connections per service |
| Active Reservations | `analytics_events_total{event_type="reservation.created"}` | Reservation volume over time |

## Local Access

| URL | Service |
|---|---|
| http://localhost:9090 | Prometheus UI |
| http://localhost:3000 | Grafana (admin / admin) |
| http://localhost:3000/d/smart-parking | Smart Parking dashboard |

## Running Phase 9

```bash
docker-compose -f infra/docker-compose.yml up --build

# Verify Prometheus targets are UP
open http://localhost:9090/targets

# Open Grafana dashboard
open http://localhost:3000
# Login: admin / admin
# Dashboard: "Smart Parking Overview" (pre-loaded)
```

## Prometheus Scrape Config Rationale

Each service exposes metrics on its main port at `/actuator/prometheus`. Prometheus reaches services by their Docker Compose service name (internal DNS). Scrape interval is 15s — fine for demo load, reduce to 30s+ in production to limit cardinality.

## Grafana Provisioning

Grafana is started with two provisioning directories mounted:
- `grafana/provisioning/datasources/` — auto-configures the Prometheus datasource on first boot
- `grafana/provisioning/dashboards/` — auto-loads the Smart Parking dashboard JSON

No manual setup required — open Grafana and the dashboard is already there.
