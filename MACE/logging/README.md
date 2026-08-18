## Logging Infrastructure Architecture

### Overview

The `logging` module provides centralized, structured observability for the Credential Rotation System. It delivers:

- **Structured JSON logging** via Logstash Logback Encoder
- **MDC context propagation** for end-to-end alert tracing
- **Automatic secret redaction** in all log output
- **Audit trail persistence** in PostgreSQL with JSONB
- **Auto-purge** of old audit events (configurable retention)
- **Performance metrics** via Micrometer + Prometheus

### Module Structure

```
logging/
├── src/main/java/com/company/rotations/logging/
│   ├── config/
│   │   ├── LoggingAutoConfiguration.java    # Spring auto-configuration
│   │   ├── MetricsConfiguration.java         # Micrometer metrics beans
│   │   └── PipelineDurationTracker.java      # Pipeline timing
│   ├── converter/
│   │   └── SecretRedactionConverter.java     # Logback secret redaction
│   ├── filter/
│   │   └── MdcLoggingFilter.java             # MDC context population
│   ├── model/
│   │   └── AuditEvent.java                   # Audit event JPA entity
│   ├── repository/
│   │   └── AuditEventRepository.java          # Audit event JPA repository
│   └── service/
│       ├── AuditService.java                 # Audit event persistence
│       └── AuditPurgeService.java            # Scheduled audit purge
├── src/main/resources/
│   ├── logback-spring.xml                    # Logback configuration
│   ├── application.yml                       # Logging configuration
│   ├── application-logging.yml               # Environment profiles
│   └── db/migration/V1__create_audit_events_table.sql
└── src/test/java/com/company/rotations/logging/
    ├── config/
    ├── converter/
    ├── filter/
    └── service/
```

### Key Components

1. **MdcLoggingFilter**: Intercepts every request, populates MDC with `trace_id`, `alert_id`, `client_id`, `phase`, and `step`. Cleans up MDC in a `finally` block.

2. **SecretRedactionConverter**: Logback converter that scans log messages for secret patterns (password, api_key, token, etc.) and replaces long values with `[REDACTED]`.

3. **AuditService**: Provides typed methods for all audit event types. Persists events synchronously to PostgreSQL. Fails silently on errors.

4. **AuditPurgeService**: Scheduled task (daily at 2 AM) that deletes audit events older than the configured retention period (default 90 days).

5. **MetricsConfiguration**: Exposes Micrometer metrics for Prometheus scraping (`/actuator/prometheus`).

### Auto-Configuration

The module uses `@ConditionalOnClass` to auto-configure only when Spring Boot is on the classpath. No manual bean registration is needed in consuming modules.

### Log Levels

- **INFO**: Normal pipeline flow (arrival, adapter selection, verification start/end)
- **WARN**: Dedup hits, circuit breaker trips
- **ERROR**: Validation failures, processing errors
- **AUDIT**: State transitions, security events (written to both console/file and `audit_events` table)
