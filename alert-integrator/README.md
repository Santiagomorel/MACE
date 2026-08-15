# Alert Integrator Module

## Overview

The `alert-integrator` module provides real-time alert ingestion for the Credential Rotation System. It receives alerts from security scanners via webhooks, normalizes them into a generic model, deduplicates at event and secret levels, and dispatches them to a worker pool for downstream processing.

### Key Features

- **Webhook-based ingestion** — real-time alerts from providers (GitGuardian, and extensible to others)
- **Adapter pattern** — pluggable provider adapters that map raw payloads to `GenericAlertModel`
- **Two-level deduplication** — event-level (TTL-based) and secret-level (state-based cooldowns)
- **Dual authentication** — HMAC signature validation + IP/CIDR whitelist
- **Worker pool** — configurable fixed-size thread pool with backpressure
- **Dead Letter Queue** — PostgreSQL-backed DLQ with retry tracking and automatic cleanup
- **Metrics** — Micrometer metrics + Prometheus scraping at `/actuator/prometheus`
- **Audit trail** — structured audit events persisted to PostgreSQL

## Module Structure

```
alert-integrator/
├── src/main/java/com/company/rotations/alerting/
│   ├── AlertIntegratorApplication.java     # Spring Boot application entry point
│   ├── AlertMetricsCollector.java          # In-memory metrics with Prometheus export
│   ├── adapter/
│   │   ├── AdapterRegistry.java            # Auto-discovers AlertAdapter beans
│   │   ├── AlertAdapter.java (SPI)         # Interface: toGenericAlert() + getProviderName()
│   │   ├── DefaultAdapter.java             # Fallback adapter for unknown sources
│   │   └── GitGuardianAdapter.java         # GitGuardian API v2 payload mapper
│   ├── config/
│   │   ├── CorsConfig.java                 # CORS configuration
│   │   ├── GlobalExceptionHandler.java     # @ControllerAdvice for REST errors
│   │   ├── WebConfig.java                  # API key interceptor registration
│   │   ├── WorkerPoolConfig.java           # WorkerPool startup/shutdown hooks
│   │   ├── BusinessException.java          # Business logic error
│   │   ├── TechnicalException.java         # Unexpected system error
│   │   ├── ValidationException.java        # Request validation error
│   │   └── ErrorResponse.java              # Standardized error response DTO
│   ├── controller/
│   │   ├── WebhookController.java          # Main webhook endpoint (POST /api/v1/alerts)
│   │   ├── SignatureValidator.java         # HMAC-SHA256 signature verification
│   │   └── IpWhitelistValidator.java       # IP/CIDR-based access control
│   ├── dedup/
│   │   ├── EventDedupService.java          # Level 1: sourceEventId-based, TTL cache
│   │   └── SecretDedupService.java         # Level 2: valueHash+repo, state-driven cooldowns
│   ├── dlq/
│   │   ├── AlertDLQEntry.java              # JPA entity: alert_dlq table
│   │   ├── AlertDLQRepository.java         # JPA repository for DLQ
│   │   └── DeadLetterQueueService.java     # DLQ operations + scheduled cleanup (daily 2 AM)
│   ├── dto/
│   │   └── AlertRequest.java               # REST request DTO
│   ├── interceptor/
│   │   └── ApiKeyInterceptor.java          # X-API-Key authentication for /api/v1/admin/**
│   ├── model/
│   │   └── WebhookPayload.java             # Record: alert + rawBody + source + receivedAt
│   ├── validation/
│   │   ├── ValidEnum.java                  # Custom JSR-303 constraint annotation
│   │   └── ValidEnumValidator.java         # Enum validation implementation
│   └── worker/
│       └── WorkerPool.java                 # Fixed-size executor with DLQ backpressure
├── src/main/resources/
│   └── application.yml                     # All configuration (webhook path, cooldowns, etc.)
└── src/test/java/com/company/rotations/alerting/
    ├── adapter/        — AdapterRegistryTest, GitGuardianAdapterTest, DefaultAdapterTest
    ├── config/         — CorsConfigTest, WebConfigTest, GlobalExceptionHandlerTest, etc.
    ├── controller/     — WebhookControllerTest, SignatureValidatorTest, IpWhitelistValidatorTest, WebhookPipelineFullTest
    ├── dedup/          — EventDedupServiceTest, SecretDedupServiceTest
    ├── dlq/            — DeadLetterQueueServiceTest, AlertDLQEntryTest
    ├── interceptor/    — ApiKeyInterceptorTest
    ├── worker/         — WorkerPoolTest
    └── validation/     — ValidEnumValidatorTest
```

## Architecture

```
HTTP Webhook ──▶ Signature Validation ──▶ IP Whitelist Check
                                     │
                                     ▼
                            Source Detection
                                     │
                                     ▼
                    ┌────────────────────────┐
                    │ Event Dedup (TTL 5min)  │── duplicate ──▶ 200 OK "duplicate_skipped"
                    └────────────────────────┘
                                     │ fresh
                                     ▼
                    ┌──────────────────────────────────────┐
                    │ Secret Dedup (state-based cooldowns)  │
                    │                                      │
                    │ false_positive → 24h cooldown        │── cooldown active ──▶ 200 OK "secret_dedup_cooldown"
                    │ in_progress  → immediate skip        │── in progress ──────▶ 200 OK "secret_in_progress"
                    │ true_positive + done → 1h cooldown   │── expired ──────────▶ proceed
                    │ new secret                  → proceed │── no entry ────────▶ proceed
                    └──────────────────────────────────────┘
                                     │ proceed
                                     ▼
                            Adapter Lookup
                                │
                    ┌───────────┼───────────┐
                    │           │           │
                    ▼           ▼           ▼
              GitGuardian  Default     Future (Snyk, etc.)
              Adapter      Adapter
                    │           │
                    └───────────┘
                         │
                         ▼
                GenericAlertModel
                         │
                         ▼
                WorkerPool.submit()
                         │
                  ┌──────┴──────┐
                  │  Workers    │
                  │  (configurable)│
                  └─────────────┘
                         │
                         ▼
               Credential Verifier (downstream)

               [On error at any stage]
                         │
                         ▼
               DeadLetterQueueService
               (stored in alert_dlq table)
```

## Deduplication Strategy

### Level 1 — Event Dedup

- **Key**: Provider event ID (`sourceEventId`)
- **Cache**: Caffeine in-memory, TTL-based (default 5 minutes)
- **Purpose**: Prevents re-processing of the same event during provider retries

### Level 2 — Secret Dedup

- **Key**: `valueHash + repository` (composite key)
- **Cache**: Caffeine in-memory, maximum 500,000 entries, 24-hour eviction
- **Purpose**: Prevents processing of the same secret within cooldown periods driven by the credential verifier's previous output:

| Verifier Result | Cooldown | Behavior on Re-detection |
|----------------|----------|--------------------------|
| `false_positive` | 24 hours | Skip — secrets flagged as FP should not re-alert frequently |
| `true_positive` + action completed | 1 hour | Skip — secret was confirmed and remediated |
| `in_progress` | Immediate skip | Skip — already being processed |
| New secret | N/A | Proceed — first detection |

Cooldowns are configurable. Cooldown expiry triggers re-registration as `PROCESSING` status.

## Adding a New Provider Adapter

1. Create a class implementing `AlertAdapter`:

```java
@Component
public class SnykAdapter implements AlertAdapter {
    @Override
    public String getProviderName() {
        return "snyk";
    }

    @Override
    public GenericAlertModel toGenericAlert(Map<String, Object> rawPayload) {
        // Map Snyk payload to GenericAlertModel
    }
}
```

2. Annotate with `@Component` — the `AdapterRegistry` auto-discovers all `AlertAdapter` beans via Spring constructor injection.

3. The `WebhookController` route is source-based: it reads the `source` field from the incoming webhook payload, looks up the adapter by name, and calls `toGenericAlert()`. Unknown sources fall back to `DefaultAdapter`.

4. Optionally, configure a provider-specific signature secret and IP whitelist in `application.yml`:

```yaml
app:
  providers:
    snyk:
      shared-secret: ${SNYK_WEBHOOK_SECRET}
      allowed-ips: ${SNYK_ALLOWED_IPS}
```

## Configuration

All configuration is in `src/main/resources/application.yml`. Key properties:

| Property | Default | Description |
|----------|---------|-------------|
| `app.alerting.webhook.path` | `/api/v1/alerts` | Webhook endpoint path |
| `app.alerting.worker-pool-size` | `5` | Number of worker threads |
| `app.alerting.queue-max-size` | `1000` | Max worker queue capacity |
| `app.alerting.event-dedup-ttl-minutes` | `5` | Event dedup TTL in minutes |
| `app.alerting.secret-dedup.false-positive-cooldown-hours` | `24` | FP cooldown duration |
| `app.alerting.secret-dedup.true-positive-cooldown-hours` | `1` | TP cooldown duration |
| `app.providers.gitguardian.shared-secret` | `changeme` | HMAC secret (env: `GITGUARDIAN_WEBHOOK_SECRET`) |
| `app.providers.gitguardian.signature-header` | `X-GitGuardian-Signature` | Signature header name |
| `app.providers.gitguardian.allowed-ips` | `72.14.199.0/24, 184.172.192.0/24` | IP whitelist (comma-separated) |
| `app.admin.api-key` | — | Admin API key (env: `ADMIN_API_KEY`) |
| `app.admin.api-key-enforce` | `true` | Enforce API key for `/api/v1/admin/**` |
| `app.dlq.retention-days` | `7` | DLQ entry retention period |
| `app.dlq.max-retries` | `3` | Max DLQ retry count before archival |
| `app.cors.enabled` | `true` | Enable CORS |
| `app.cors.allowed-origins` | `*` | Allowed CORS origins (comma-separated) |

Environment variables are supported via `${ENV_VAR:default}` syntax for secrets.

## API Endpoints

| Method | Path | Authentication | Description |
|--------|------|----------------|-------------|
| `POST` | `/api/v1/alerts` | HMAC signature + IP whitelist | Receive webhook alerts |
| Any | `/api/v1/admin/**` | `X-API-Key` header | Admin endpoints (API key required) |
| `GET` | `/actuator/health` | — | Spring Boot health check |
| `GET` | `/actuator/prometheus` | — | Prometheus metrics scrape |

### Webhook Response Codes

| Status | Body | When |
|--------|------|------|
| `200 OK` | `{"status": "duplicate_skipped"}` | Event dedup hit |
| `200 OK` | `{"status": "secret_dedup_cooldown"}` | Secret dedup cooldown active |
| `200 OK` | `{"status": "secret_in_progress"}` | Secret already being processed |
| `200 OK` | `{"status": "accepted"}` | Alert queued for processing |
| `200 OK` | `{"status": "processing_failed"}` | Pipeline error (alert sent to DLQ) |
| `401 Unauthorized` | `{"error": "INVALID_SIGNATURE"}` | HMAC signature mismatch |
| `403 Forbidden` | `{"error": "IP_FORBIDDEN"}` | Source IP not in whitelist |

## Database

The module uses PostgreSQL via Spring Data JPA. The DLQ requires the `alert_dlq` table (UUID primary key, JSONB payload column). The `logging` module's `audit_events` table is also used for audit trail.

## Metrics

`AlertMetricsCollector` exposes the following counters and gauges (visible at `/actuator/prometheus`):

- `totalWebhooksReceived` — webhooks received
- `totalWebhooksRejected` — rejected (bad signature / forbidden IP)
- `totalEventsDeduped` — event-level dedup hits
- `totalSecretsDedupedCooldown` — secret dedup cooldown skips
- `totalSecretsDedupedInProgress` — secret dedup in-progress skips
- `totalAlertsProcessed` — successfully processed alerts
- `totalAlertsFailed` — processing failures
- `totalAlertsSentToDlq` — alerts sent to DLQ
- `avgPipelineDurationMs` / `minPipelineDurationMs` / `maxPipelineDurationMs` — pipeline timing
- `adapterRouteCounts` — per-adapter routing counts
- `sourceCounts` — per-source counts

Metrics are also logged every 5 minutes at INFO level.

## Testing

223 tests across 26 test classes, 100% coverage of all public APIs:

- **Unit tests** — individual components (adapters, validators, services, DLQ)
- **Integration tests** — `WebhookPipelineFullTest` exercises the full ingestion pipeline with mocked dependencies
- **Config tests** — `CorsConfigTest`, `WebConfigTest`, `WorkerPoolConfigTest` validate Spring configuration

Run with `mvn test`.

## Dependencies

- **Internal**: `shared-models` (GenericAlertModel), `shared-spi` (AlertAdapter interface), `logging` (AuditService)
- **External**: Spring Boot Web, Spring Data JPA, Caffeine cache, PostgreSQL driver, Micrometer + Prometheus, Logstash Logback Encoder

## Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Caffeine cache does not share state across instances | Medium | Migrate to Redis when multi-instance deployment is needed |
| Worker pool saturation under alert spikes | Medium | Queue with backpressure; full queue triggers DLQ entry |
| DLQ table grows rapidly | Low | Scheduled cleanup at 2 AM daily (7-day retention by default) |
| Raw payload in DLQ consumes space | Low | JSONB storage; configurable retention with auto-cleanup |
| Secret value hash collision | Very Low | SHA-256 hash; composite key includes repository |
