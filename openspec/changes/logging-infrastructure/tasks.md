## 1. Module Setup and Configuration

- [ ] [R1] 1.1 Create package organization for logging module (`logging`, `logging.filter`, `logging.converter`, `logging.model`, `logging.repository`, `logging.service`)
- [ ] [R1] 1.2 Add `logstash-logback-encoder` dependency to build configuration
- [ ] [R1] 1.3 Add `micrometer-registry-prometheus` dependency to build configuration (or choose appropriate registry)
- [ ] [R1] 1.4 Add `spring-boot-starter-actuator` dependency
- [ ] [R1] 1.5 Create `logback-spring.xml` with JSON layout via LogstashEncoder
- [ ] [R1] 1.6 Configure async appender in logback for non-blocking log output
- [ ] [R1] 1.7 Configure rolling file policy (time-based, 7-day retention for files)

## 2. MDC Contextual Fields

- [ ] [R1] 2.1 Create `MdcLoggingFilter` extending `OncePerRequestFilter`
- [ ] [R1] 2.2 Generate `trace_id` if not present in `X-Trace-Id` request header
- [ ] [R1] 2.3 Extract or generate `alert_id` from request context
- [ ] [R1] 2.4 Extract `client_id` from authentication context or request metadata
- [ ] [R1] 2.5 Register filter as Spring bean with appropriate order
- [ ] [R1] 2.6 Ensure MDC fields are cleared in `finally` block
- [ ] [R1] 2.7 Write unit tests for MDC filter (with/without trace_id, with/without client_id, cleanup on exception)

## 3. Secret Redaction

- [ ] [R2] 3.1 Create `SecretRedactionConverter` extending `LayoutConverter` for Logback
- [ ] [R2] 3.2 Implement regex pattern matching for secret keys: secret, password, key, token, access_key, api_key, private_key
- [ ] [R2] 3.3 Replace values of 20+ characters with `[REDACTED]`
- [ ] [R2] 3.4 Register converter in `logback-spring.xml` via custom encoder
- [ ] [R2] 3.5 Write unit tests for redaction (various secret patterns, short values passthrough, no secrets passthrough)

## 4. Audit Event Model

- [ ] [R2] 4.1 Create `AuditEvent` JPA entity with all fields from design
- [ ] [R2] 4.2 Create `AuditEventRepository` with custom query methods
  - `deleteByCreatedAtBefore(LocalDateTime date)` for auto-purge
  - `findByClientId(String clientId)` for tenant query
  - `findByAlertId(String alertId)` for alert trace
  - `findByEventType(String eventType)` for type-based queries
- [ ] [R2] 4.3 Create database migration script (Flyway or Liquibase) for `audit_events` table
- [ ] [R2] 4.4 Add database indexes: `idx_audit_client`, `idx_audit_alert`, `idx_audit_created`, `idx_audit_data (GIN)`
- [ ] [R2] 4.5 Write unit tests for entity mapping and repository queries

## 5. Audit Service

- [ ] [R2] 5.1 Create `AuditService` with methods for each event type:
  - `logWebhookReceived(eventData)`
  - `logVerificationStarted(eventData)`
  - `logVerificationCompleted(eventData)`
  - `logRuleEvaluated(eventData)`
  - `logActionExecuted(eventData)`
  - `logDlqEnqueued(eventData)`
  - `logDedupHit(eventData)`
- [ ] [R2] 5.2 Implement synchronous JPA save in each method
- [ ] [R2] 5.3 Log errors if audit persist fails (do not throw — fail silently for audit)
- [ ] [R2] 5.4 Write unit tests for audit service (all event types, error handling on persist failure)

## 6. Audit Auto-Purge

- [ ] [R2] 6.1 Create `AuditPurgeService` with `@Scheduled` task
- [ ] [R2] 6.2 Configure cron: `0 0 2 * * ?` (daily at 2 AM)
- [ ] [R2] 6.3 Implement purge using `auditEventRepository.deleteByCreatedAtBefore(LocalDateTime.now().minusDays(90))`
- [ ] [R2] 6.4 Log purge count (deleted events)
- [ ] [R2] 6.5 Make retention period configurable via `application.yml` (`app.logging.audit-retention-days`, default 90)
- [ ] [R2] 6.6 Write unit tests for purge (verify deletion count, verify 90-day cutoff)

## 7. Metrics

- [ ] [R2] 7.1 Configure Micrometer timers/counters/gauges as specified in spec
- [ ] [R2] 7.2 Instrument pipeline duration in the logging module (for end-to-end tracking)
- [ ] [R2] 7.3 Configure `/actuator/prometheus` endpoint in `application.yml`
- [ ] [R2] 7.4 Configure health endpoint in `application.yml`
- [ ] [R2] 7.5 Write integration test for `/actuator/prometheus` endpoint (verify metrics are exposed)

## 8. Auto-Configuration

- [ ] [R2] 8.1 Create `LoggingAutoConfiguration` with `@Configuration` and `@ConditionalOnClass`
- [ ] [R2] 8.2 Register `MdcLoggingFilter` as bean
- [ ] [R2] 8.3 Register `SecretRedactionConverter` via Logback configuration
- [ ] [R2] 8.4 Register `AuditService` as bean
- [ ] [R2] 8.5 Register `AuditPurgeService` as bean
- [ ] [R2] 8.6 Verify that no module-specific logging code is required for integration
- [ ] [R2] 8.7 Write integration test: verify all components are auto-configured

## 9. Integration with Other Modules

- [ ] [R2] 9.1 Update alert-integrator tasks: inject `AuditService` for webhook and dedup audit events
- [ ] [R2] 9.2 Update verifier tasks: inject `AuditService` for verification audit events
- [ ] [R2] 9.3 Update decision-engine tasks: inject `AuditService` for rule evaluation audit events
- [ ] [R2] 9.4 Update action-executor tasks: inject `AuditService` for action audit events
- [ ] [R2] 9.5 Set `phase` MDC field in each module via `AuditService.log*()` methods
- [ ] [R2] 9.6 Verify audit trail is complete for a full pipeline flow (webhook → verify → decision → action)

## 10. Testing

- [ ] [R2] 10.1 Write unit tests for MDC filter (all scenarios)
- [ ] [R2] 10.2 Write unit tests for secret redaction (all secret patterns)
- [ ] [R2] 10.3 Write unit tests for audit service (all event types, error handling)
- [ ] [R2] 10.4 Write unit tests for audit purge (correct retention period)
- [ ] [R2] 10.5 Write integration test: full audit trail for a synthetic alert through all 4 phases
- [ ] [R2] 10.6 Write integration test: verify JSON log format includes all MDC fields
- [ ] [R2] 10.7 Write integration test: verify secret redaction in log output

## 11. Configuration

- [ ] [R2] 11.1 Add logging configuration to `application.yml`:
  - Log levels per package (`com.app.logging=DEBUG`, `com.app.alerting=INFO`, etc.)
  - Audit retention period (`app.logging.audit-retention-days: 30`)
  - Actuator endpoints configuration
  - Prometheus registry configuration
- [ ] [R2] 11.2 Document all configurable properties
- [ ] [R2] 11.3 Add example `application-logging.yml` for different environments (dev, staging, prod)

## 12. Documentation

- [ ] [R2] 12.1 Document the logging architecture in a module README
- [ ] [R2] 12.2 Document MDC fields and their sources
- [ ] [R2] 12.3 Document audit event types and their expected `event_data` structure
- [ ] [R2] 12.4 Document how other modules integrate with the logging infrastructure
- [ ] [R2] 12.5 Document metrics and their meanings (for Grafana dashboard)
