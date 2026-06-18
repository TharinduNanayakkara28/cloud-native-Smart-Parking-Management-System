# Monitoring Implementation Details

## Micrometer + Prometheus Integration

Spring Boot 3 uses Micrometer as its metrics facade. Adding `micrometer-registry-prometheus` to the classpath auto-configures:

1. A `PrometheusMeterRegistry` bean
2. An `/actuator/prometheus` endpoint that renders all metrics in the Prometheus text exposition format

**Key auto-instrumented metrics (zero config):**

| Metric | Instrument | Source |
|---|---|---|
| `http_server_requests_seconds` | Histogram | Every Spring MVC request |
| `jvm_memory_used_bytes` | Gauge | JVM heap + non-heap |
| `jvm_gc_pause_seconds` | Histogram | GC pause durations |
| `hikaricp_connections_active` | Gauge | HikariCP pool (JPA services) |
| `kafka_consumer_fetch_manager_records_lag` | Gauge | Spring Kafka consumers |
| `process_cpu_usage` | Gauge | Process CPU |
| `process_uptime_seconds` | Gauge | Uptime |

**Application tag:** Every metric is tagged with `application=<service-name>` so Grafana queries can filter by service:
```yaml
management:
  metrics:
    tags:
      application: ${spring.application.name}
```

## Prometheus Scrape Configuration

`infra/prometheus/prometheus.yml` defines one scrape job per service. Each job:
- Scrapes every 15 seconds
- Hits `/actuator/prometheus` on the service's main port
- Tags metrics with the job name (maps to service name in Grafana)

## Grafana Dashboard Architecture

```
grafana/
├── provisioning/
│   ├── datasources/
│   │   └── prometheus.yml    ← Auto-connects to Prometheus on startup
│   └── dashboards/
│       └── provider.yml      ← Tells Grafana where to find dashboard JSON files
└── dashboards/
    └── smart-parking.json    ← The actual dashboard, loaded on startup
```

**Why provisioning instead of manual import?**
The provisioned dashboard is available immediately after `docker-compose up` — no login, no manual import. For a portfolio project this is important: reviewers can run one command and see metrics without additional setup.

## Key Grafana Queries

**Reservation throughput (5-min rate):**
```promql
sum(rate(http_server_requests_seconds_count{application="reservation-service", uri="/reservations", method="POST", status="201"}[5m]))
```

**Payment success rate:**
```promql
sum(rate(http_server_requests_seconds_count{application="payment-service", status="200"}[5m])) /
sum(rate(http_server_requests_seconds_count{application="payment-service"}[5m]))
```

**Kafka consumer lag across all groups:**
```promql
sum by (group_id) (kafka_consumer_fetch_manager_records_lag{application=~".*-service"})
```

**P99 gateway latency:**
```promql
histogram_quantile(0.99,
  sum by (le) (rate(http_server_requests_seconds_bucket{application="api-gateway"}[5m]))
)
```

## Production Upgrade Path

| Concern | Phase 9 | Production recommendation |
|---|---|---|
| Metrics storage | Prometheus (in-process, ephemeral) | Thanos / VictoriaMetrics for long-term retention |
| Alerting | None | Prometheus Alertmanager + PagerDuty/Slack |
| Log aggregation | Container stdout | ELK stack (Elasticsearch + Logstash + Kibana) |
| Distributed tracing | None | OpenTelemetry + Jaeger / Tempo |
| Dashboard persistence | Volume mount | Grafana Cloud or managed Grafana |
