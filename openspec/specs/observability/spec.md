## Purpose

TBD

## Requirements

### Requirement: Structured logging with Logback JSON
The system SHALL use Logback with a JSON layout appender for structured logging output.

#### Scenario: JSON log format
- **WHEN** an application event is logged
- **THEN** the log output is valid JSON with fields: `timestamp`, `level`, `logger`, `message`, `thread`, `tenantId`, `alertId`
- **AND** the JSON is parseable by standard log aggregation tools (e.g., Datadog, CloudWatch, ELK)

#### Scenario: MDC context propagation
- **WHEN** a request enters the system
- **THEN** the MDC (Mapped Diagnostic Context) is populated with `tenantId`, `alertId`, and `sessionId`
- **AND** all log lines within the request lifecycle include these context fields automatically

#### Scenario: Secret redaction in logs
- **WHEN** a log line contains a value matching a secret pattern (AWS access key pattern `AKIA[A-Z0-9]{16}`, IAM user patterns, etc.)
- **THEN** the `SecretRedactingConverter` replaces the matching value with `***REDACTED***`
- **AND** the redaction is applied at the Logback converter level for all appenders

### Requirement: Spring Actuator health and metrics
The system SHALL expose Spring Actuator endpoints for health checks, readiness, and metrics.

#### Scenario: Health endpoint
- **WHEN** the `/actuator/health` endpoint is called
- **THEN** it returns the application health status with components: `db` (PostgreSQL), `secretsManager` (AWS), `diskSpace`
- **AND** the `show-details` is set to `when-authorized` (not exposed by default)

#### Scenario: Liveness and readiness probes
- **WHEN** Kubernetes or container orchestrator checks liveness/readiness
- **THEN** `/actuator/health/liveness` returns `UP` when the application is running
- **AND** `/actuator/health/readiness` returns `UP` when all dependencies (DB, Secrets Manager) are connected

#### Scenario: Metrics endpoint
- **WHEN** the `/actuator/metrics` endpoint is called
- **THEN** it exposes Micrometer metrics including: HTTP request counts, DB query times, alert processing times, rotation operation counts
- **AND** the `/actuator/prometheus` endpoint provides metrics in Prometheus scrape format

### Requirement: Micrometer metrics collection
The system SHALL use Micrometer to collect and expose application metrics.

#### Scenario: HTTP request metrics
- **WHEN** an HTTP request is processed
- **THEN** Micrometer records the request duration, status code, and method
- **AND** metrics are tagged with `http.status`, `http.method`, and `uri`

#### Scenario: Domain-specific metrics
- **WHEN** an alert is ingested, verified, or rotated
- **THEN** Micrometer records counter metrics: `alerts.ingested`, `alerts.deduplicated`, `verification.completed`, `rotation.completed`, `rotation.failed`
- **AND** metrics are tagged with `provider`, `credentialType`, and `result`

#### Scenario: Prometheus endpoint
- **WHEN** the `/actuator/prometheus` endpoint is called
- **THEN** it returns all collected metrics in Prometheus exposition format
- **AND** the metrics include standard JVM metrics (GC, threads, memory) and application-specific metrics

### Requirement: Audit trail via audit_events table
The system SHALL maintain an `audit_events` table to log all security-relevant and business-critical events.

#### Scenario: Audit event recording
- **WHEN** a security-relevant event occurs (credential access, rotation start/finish, verification result)
- **THEN** an `AuditEvent` entity is persisted to the `audit_events` table
- **AND** the event includes: `eventType`, `tenantId`, `alertId`, `timestamp`, `details` (JSONB), `userId` (if available)

#### Scenario: Audit event types
- **WHEN** different events occur
- **THEN** the following event types are recorded: `ALERT_INGESTED`, `ALERT_DEDUPLICATED`, `CREDENTIAL_VERIFIED`, `CREDENTIAL_EXPIRED`, `ROTATION_STARTED`, `ROTATION_COMPLETED`, `ROTATION_FAILED`, `ESCALATION_TRIGGERED`, `CREDENTIAL_ACCESSED`

#### Scenario: Audit event query
- **WHEN** an audit event is queried by tenantId and event type
- **THEN** the system returns matching events from the `audit_events` table
- **AND** the query uses indexed columns for efficient retrieval

### Requirement: Log retention and auto-purge
The system SHALL enforce a 90-day log retention policy with automatic purging of old audit events.

#### Scenario: Auto-purge of old events
- **WHEN** the scheduled purger runs (daily)
- **THEN** it deletes `audit_events` records older than 90 days
- **AND** the purge operation is logged as an audit event itself

#### Scenario: Retention configuration
- **WHEN** the retention period is configured
- **THEN** it is set to 90 days by default
- **AND** the retention period is configurable via `application.yml` (`audit.retention-days`)
