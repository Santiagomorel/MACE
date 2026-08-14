## Metrics Reference

### Pipeline Metrics

| Metric | Type | Description |
|---|---|---|
| `app.pipeline.duration` | Timer | End-to-end alert processing duration |

### Deduplication Metrics

| Metric | Type | Description |
|---|---|---|
| `app.dedup.hits` | Counter | Number of duplicate alerts detected |
| `app.dedup.misses` | Counter | Number of unique alerts passing dedup |

### Webhook Metrics

| Metric | Type | Description |
|---|---|---|
| `app.webhook.received` | Counter | Total webhooks received |
| `app.webhook.failed` | Counter | Webhooks failing validation |

### Circuit Breaker Metrics

| Metric | Type | Description |
|---|---|---|
| `app.circuit.breaker.state` | Gauge | Circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN) |

### Audit Metrics

| Metric | Type | Description |
|---|---|---|
| `app.audit.events.count` | Gauge | Total audit events tracked |

### Prometheus Endpoint

Metrics are exposed at `/actuator/prometheus` in Prometheus text format.

Example Prometheus scrape config:
```yaml
scrape_configs:
  - job_name: 'rotation-system'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['localhost:8080']
```

### Grafana Dashboard

Recommended panels:
- **Pipeline Latency**: `histogram_quantile(0.95, rate(app_pipeline_duration_seconds_bucket[5m]))`
- **Alert Throughput**: `rate(app_webhook_received_total[5m])`
- **Dedup Rate**: `rate(app_dedup_hits_total[5m]) / (rate(app_dedup_hits_total[5m]) + rate(app_dedup_misses_total[5m]))`
- **Webhook Failures**: `rate(app_webhook_failed_total[5m])`
- **Audit Events**: `increase(app_audit_events_count[1h])`
