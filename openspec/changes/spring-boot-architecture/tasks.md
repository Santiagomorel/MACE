## 1. Parent POM and Module Scaffolding

- [ ] 1.1 Create parent POM with Spring Boot 3.3.x parent, Java 21, and dependencyManagement BOM
- [ ] 1.2 Define all 6 modules in parent POM: shared/models, shared/spi, alert-integrator, verification-engine, decision-engine, action-executor
- [ ] 1.3 Configure dependencyManagement with versions: Spring Boot 3.3.x, AWS SDK v2, Drools/KIE, Testcontainers, JaCoCo
- [ ] 1.4 Configure pluginManagement: maven-compiler-plugin (Java 21), maven-surefire-plugin, JaCoCo (70% general gate)
- [ ] 1.5 Verify `mvn clean package` builds all modules in topological order from parent directory

## 2. Shared Models

- [ ] 2.1 Create `shared/shared-models` module with package `com.company.rotations.models`
- [ ] 2.2 Implement `Alert` entity with JPA annotations (id, providerName, credentialType, tenantId, status, payload, rawPayload, receivedAt, state)
- [ ] 2.3 Implement `AlertType` enum (AWS_ACCESS_KEY, IAM_USER, RDS_CREDENTIAL, GENERIC)
- [ ] 2.4 Implement `AlertStatus` enum (PENDING, PROCESSING, VERIFIED, DECIDED, ROTATING, COMPLETED, FAILED, IN_DLQ)
- [ ] 2.5 Implement `VerificationResult` entity (alertId, verified, reason, severityScope, blastRadius, credentialType, tenantId, provider, timestamp)
- [ ] 2.6 Implement `DecisionOutput` model (alertId, decision: rotate/no_action/escalate, severity, reason, playbookName)
- [ ] 2.7 Implement `RotationAction` entity (alertId, credentialType, status: PENDING/ROTATING/SUCCESS/FAIL/ESCALATE, provider, attempts, timeout, createdAt, updatedAt)
- [ ] 2.8 Implement `AuditEvent` entity (eventType, tenantId, alertId, timestamp, details: JSONB, userId)
- [ ] 2.9 Implement `AuditEventType` enum (ALERT_INGESTED, ALERT_DEDUPLICATED, CREDENTIAL_VERIFIED, CREDENTIAL_EXPIRED, ROTATION_STARTED, ROTATION_COMPLETED, ROTATION_FAILED, ESCALATION_TRIGGERED, CREDENTIAL_ACCESSED)
- [ ] 2.10 Implement `GenericAlertModel` with all fields: eventId, source, sourceEventId, detectedSecret(type, valueHash, pattern), context(repository, file, commit, line, visibility, foundAt), providerSeverity, detectorState(isNew, previouslyFlagged, flagCount), receivedAt, rawPayload
- [ ] 2.11 Write unit tests for all models (serialization/deserialization, enum values, toString masking for sensitive fields)

## 3. Shared SPIs

- [ ] 3.1 Create `shared/shared-spi` module with package `com.company.rotations.spi`
- [ ] 3.2 Implement `AlertAdapter` interface with version `1.0.0`: `GenericAlertModel toGenericAlert(Map<String, Object> rawPayload)`, `String getProviderName()`
- [ ] 3.3 Implement `VerificationProvider` interface with version `1.0.0`: `VerificationResult verify(String credentialType, Map<String, String> credentials, String tenantId)`
- [ ] 3.4 Implement `PlaybookManager` interface with version `1.0.0`: `Playbook loadPlaybook(String credentialType)`, `List<String> getPlaybookSteps(String credentialType)`
- [ ] 3.5 Implement `RotationService` interface with version `1.0.0`: `RotationAction rotate(String credentialType, Map<String, String> credentials, String tenantId)`
- [ ] 3.6 Implement `NotificationChannel` interface with version `1.0.0`: `void send(String message, Map<String, String> context)`
- [ ] 3.7 Configure SPI module version as `1.0.0` in its POM
- [ ] 3.8 Write unit tests for SPI interface contract validation (each interface has all required methods)

## 4. Alert Integrator Module

- [ ] 4.1 Create `alert-integrator` module with package `com.company.rotations.alerting`
- [ ] 4.2 Add POM dependencies to `shared/models` and `shared/spi`
- [ ] 4.3 Configure dependencies: Spring Boot web, Caffeine, PostgreSQL driver, Jackson
- [ ] 4.4 Create `AlertController` with `POST /api/v1/alerts` endpoint
- [ ] 4.5 Implement HMAC-SHA256 signature validation for webhook requests (`X-Signature` header)
- [ ] 4.6 Implement IP whitelist validation for webhook requests (configurable per provider)
- [ ] 4.7 Create `AdapterRegistry` singleton that maps provider names to AlertAdapter implementations
- [ ] 4.8 Implement `GitGuardianAdapter` that maps GitGuardian v2 API payload to GenericAlertModel
- [ ] 4.9 Implement `DefaultAdapter` as fallback for unknown sources
- [ ] 4.10 Create `EventDedupService` using Caffeine cache (key: sourceEventId, TTL: configurable, default 5min)
- [ ] 4.11 Create `SecretDedupService` using Caffeine + DB (key: valueHash + repository, state-based cooldowns)
- [ ] 4.12 Create `WorkerPool` with configurable size (default 5) and bounded queue (default 1000)
- [ ] 4.13 Implement backpressure: return HTTP 429 when queue is full
- [ ] 4.14 Create `alert_dlq` table and `DeadLetterQueueService`
- [ ] 4.15 Integrate DLQ into processing pipeline (insert on failures)
- [ ] 4.16 Implement DLQ cleanup scheduled task (delete entries older than 7 days)
- [ ] 4.17 Create `WebhookPipeline` orchestrating: validate auth → event dedup → adapter → secret dedup → worker queue
- [ ] 4.18 Write unit tests for AlertController (valid payload, invalid signature, missing signature, invalid IP, missing required fields)
- [ ] 4.19 Write unit tests for GitGuardianAdapter (full mapping, partial mapping, missing optional fields)
- [ ] 4.20 Write unit tests for AdapterRegistry (found adapter, missing adapter → NotFoundException)
- [ ] 4.21 Write unit tests for EventDedupService (hit, miss, TTL expiration, concurrent access)
- [ ] 4.22 Write unit tests for SecretDedupService (all state transitions, cooldown periods)
- [ ] 4.23 Write unit tests for WorkerPool (concurrent processing, backpressure, worker failure)
- [ ] 4.24 Write integration test for end-to-end webhook flow with mocked adapter and cache
- [ ] 4.25 Write integration test for DLQ flow (invalid payload → DLQ entry)
- [ ] 4.26 Add sample webhook payloads in `src/test/resources/webhooks/` for GitGuardian

## 5. Verification Engine Module

- [ ] 5.1 Create `verification-engine` module with package `com.company.rotations.verification`
- [ ] 5.2 Add POM dependencies to `shared/models`, `shared/spi`, and `alert-integrator`
- [ ] 5.3 Configure dependencies: AWS SDK v2 (STS), Spring Boot
- [ ] 5.4 Implement `AwsStsVerificationProvider` that calls AWS STS GetCallerIdentity
- [ ] 5.5 Implement `BlastRadiusCalculator` that evaluates credential policies to determine scope
- [ ] 5.6 Implement `SeverityRuleEngine` that applies per-tenant severity floors
- [ ] 5.7 Implement `VerificationService` orchestrating provider call + blast radius + severity rules
- [ ] 5.8 Create verification result repository for storing VerificationResult
- [ ] 5.9 Write unit tests for AwsStsVerificationProvider (active key, expired key, invalid key, provider unavailable)
- [ ] 5.10 Write unit tests for BlastRadiusCalculator (AdministratorAccess → critical, ReadOnlyAccess → low, custom policies)
- [ ] 5.11 Write unit tests for SeverityRuleEngine (tenant floor higher than calculated, tenant floor lower, no tenant rules → default)
- [ ] 5.12 Write integration tests with Testcontainers for verification result persistence

## 6. Decision Engine Module

- [ ] 6.1 Create `decision-engine` module with package `com.company.rotations.decision`
- [ ] 6.2 Add POM dependencies to `shared/models`, `shared/spi`, and `verification-engine`
- [ ] 6.3 Configure dependencies: Drools/KIE (`kie-spring`, `drools-core`), Spring Boot
- [ ] 6.4 Create default Drools rules in `src/main/resources/rules/` (evaluate verified=true+high→rotate, verified=false→no_action)
- [ ] 6.5 Implement `DefaultKieContainerProvider` that loads rules from classpath on startup
- [ ] 6.6 Implement `TenantKieContainerProvider` that maintains per-tenant KieContainer with hot-reload capability
- [ ] 6.7 Implement `PlaybookManager` that loads YAML playbooks from `src/main/resources/playbooks/`
- [ ] 6.8 Implement `RuleEngineService` that combines: Drools evaluation → severity floor → DecisionOutput
- [ ] 6.9 Create admin endpoint `GET /api/v1/admin/rules` for rule management (requires API key)
- [ ] 6.10 Write unit tests for RuleEngineService (rotate decision, no_action decision, severity floor logic)
- [ ] 6.11 Write Drools tests with KieFileSystem for dynamic rule loading
- [ ] 6.12 Write unit tests for PlaybookManager (load by credential type, missing playbook → exception)

## 7. Action Executor Module

- [ ] 7.1 Create `action-executor` module with package `com.company.rotations.actions`
- [ ] 7.2 Add POM dependencies to `shared/models`, `shared/spi`, and `decision-engine`
- [ ] 7.3 Configure dependencies: AWS SDK v2 (IAM, STS, RDS), Spring Boot, Slack SDK
- [ ] 7.4 Implement `RotationStateMachine` managing transitions: PENDING → ROTATING → SUCCESS|FAIL → ESCALATE
- [ ] 7.5 Implement `AwsRotationService` for AccessKey rotation (set_inactive → wait → create → verify)
- [ ] 7.6 Implement `AwsRotationService` for IAMUser rotation (deactivate → delete → create → verify)
- [ ] 7.7 Implement notification channels — see `action-executor-credential-rotation` tasks Section 3 for detailed tasks

## 8. Secrets Management (continued)

- [ ] 8.1 Implement `SecretVaultService` with AWS Secrets Manager client (prod/staging profile)
- [ ] 8.2 Implement `SecretVaultService` fallback to PostgreSQL encrypted columns (dev/test profile)
- [ ] 8.3 Implement `Aes256Converter` JPA AttributeConverter for column encryption
- [ ] 8.4 Implement `SecretRedactingConverter` custom Logback converter for secret pattern redaction — see `logging-infrastructure` tasks Section 3
- [ ] 8.5 Create `client_credentials` table with AES-256 encrypted columns (access_key_encrypted, secret_key_encrypted)
- [ ] 8.6 Implement `ClientCredentialRepository` for DB credential storage and retrieval
- [ ] 8.7 Implement credential retrieval with fallback: AWS SM primary → DB backup → error
- [ ] 8.8 Implement secret path convention: `/app/rotation/{path}` and `/clients/{tenantId}/{secret-type}`
- [ ] 8.9 Implement rotation policy tracking: rotated_at timestamp, 90-day configurable deadline, alert triggers
- [ ] 8.10 Configure `ENCRYPTION_MASTER_KEY` env var loading for POC mode
- [ ] 8.11 Write unit tests for Aes256Converter (encrypt/decrypt roundtrip, key loading)
- [ ] 8.12 Write unit tests for SecretVaultService (AWS SM path, DB fallback, path convention)
- [ ] 8.13 Write unit tests for SecretRedactingConverter (AWS key pattern, IAM pattern, non-matching text)
- [ ] 8.14 Write integration tests for client_credentials table with encrypted columns

## 9. Observability

> **Note:** Detailed logging implementation tasks are in `logging-infrastructure` change. This section covers integration and configuration only.

- [ ] 9.1 Integrate Logback JSON layout — see `logging-infrastructure` tasks Section 1
- [ ] 9.2 Configure MDC context propagation (tenantId, alertId, sessionId) via `OncePerRequestFilter` — see `logging-infrastructure` tasks Section 2
- [ ] 9.3 Add `SecretRedactingConverter` to Logback configuration — see `logging-infrastructure` tasks Section 3
- [ ] 9.4 Configure Spring Actuator endpoints: health, readiness, liveness, metrics, prometheus
- [ ] 9.5 Implement custom health indicator for Secrets Manager connectivity
- [ ] 9.6 Implement custom health indicator for Database connectivity
- [ ] 9.7 Configure Micrometer metrics: HTTP request metrics (duration, status, method)
- [ ] 9.8 Configure domain-specific Micrometer metrics: alerts.ingested, alerts.deduplicated, verification.completed, rotation.completed, rotation.failed
- [ ] 9.9 Integrate `AuditEventService` — see `logging-infrastructure` tasks Section 5
- [ ] 9.10 Integrate `AuditEventRepository` — see `logging-infrastructure` tasks Section 4
- [ ] 9.11 Implement scheduled audit event purger (delete events older than 90 days) — see `logging-infrastructure` tasks Section 6
- [ ] 9.12 Configure actuator `show-details=when-authorized` for prod profile
- [ ] 9.13 Write unit tests for AuditEventService — see `logging-infrastructure` tasks Section 5
- [ ] 9.14 Write integration tests for audit event persistence with Testcontainers — see `logging-infrastructure` tasks Section 5

## 10. Cross-Cutting Concerns

- [ ] 10.1 Create `@ControllerAdvice` with `@ExceptionHandler` for global error handling
- [ ] 10.2 Implement `ErrorResponse` model (timestamp, status, error, path, message, details)
- [ ] 10.3 Implement `TechnicalExceptionHandler` returning HTTP 500 without stack traces
- [ ] 10.4 Implement `BadRequestExceptionHandler` returning HTTP 400 with validation details
- [ ] 10.5 Implement URL versioning: all API endpoints under `/api/v1/`
- [ ] 10.6 Implement `ApiKeyInterceptor` for `/api/v1/admin/*` endpoints (X-API-Key header)
- [ ] 10.7 Implement CORS configuration: `*` allowed in dev, restricted origins in prod
- [ ] 10.8 Implement Bean Validation on request bodies (@Valid, @NotBlank, @EnumValue)
- [ ] 10.9 Create `GlobalExceptionHandler` unit tests (business error, technical error, validation error)
- [ ] 10.10 Create `ApiKeyInterceptor` unit tests (valid key, missing key, invalid key)

## 11. Environment Profiles

- [ ] 11.1 Create `application.yml` with common configuration
- [ ] 11.2 Create `application-dev.yml` with H2 in-memory, DEBUG logging, create-drop DDL
- [ ] 11.3 Create `application-staging.yml` with PostgreSQL, validate DDL, INFO logging
- [ ] 11.4 Create `application-prod.yml` with PostgreSQL, validate DDL, WARN framework/INFO app logging, actuator when-authorized
- [ ] 11.5 Create `application-test.yml` with Testcontainers PostgreSQL, create-drop DDL
- [ ] 11.6 Configure `spring.profiles.active` via Maven property (overridable at build time)
- [ ] 11.7 Write integration tests verifying profile-specific configuration (each profile loads correctly)

## 12. Testing Infrastructure

- [ ] 12.1 Configure JaCoCo Maven plugin with coverage gates: 70% general, 80%+ domain
- [ ] 12.2 Configure per-type coverage targets: controllers/adapters 60%, models/exceptions 90%
- [ ] 12.3 Add Testcontainers dependency to parent POM (`testcontainers-postgresql`)
- [ ] 12.4 Create base integration test class `@Testcontainers @ActiveProfiles("test")` for all modules
- [ ] 12.5 Create `@WebMvcTest` base class for controller tests
- [ ] 12.6 Configure Drools test utilities: `KieFileSystem` helper, `KieContainer` builder
- [ ] 12.7 Verify JaCoCo coverage gates fail build when thresholds not met
- [ ] 12.8 Add excluded dependencies: `junit-vintage-engine` (JUnit 4 only, no vintage)

## 13. Docker and Deployment

- [ ] 13.1 Create Dockerfile with multi-stage build (Maven 3.9 + Java 21 build stage, eclipse-temurin:21-jre-alpine runtime)
- [ ] 13.2 Configure Dockerfile to run as non-root user in runtime stage
- [ ] 13.3 Create `docker-compose.yml` with Spring Boot app + PostgreSQL 16 + health checks
- [ ] 13.4 Configure PostgreSQL dependency health check in docker-compose
- [ ] 13.5 Configure application health check against `/actuator/health` in docker-compose
- [ ] 13.6 Add `.env.example` template with required environment variables
- [ ] 13.7 Add `.gitignore` entries for `.env.*` files and secrets
- [ ] 13.8 Create GitHub Actions workflow for PR builds (`mvn clean verify`)
- [ ] 13.9 Create GitHub Actions workflow for main branch deployment (build Docker → push to registry → deploy)
- [ ] 13.10 Configure CI coverage gate: fail if JaCoCo thresholds not met
- [ ] 13.11 Create blue-green deployment documentation for Phase 3

## 14. Database Migrations

- [ ] 14.1 Create V1__init_schema.sql migration (alerts, alert_dlq, verification_results, audit_events, client_credentials, rotation_actions tables)
- [ ] 14.2 Add indexes on key columns: alerts(tenantId, status), verification_results(alertId), audit_events(tenantId, eventType, timestamp), client_credentials(tenantId), rotation_actions(alertId)
- [ ] 14.3 Add JSONB GIN index on audit_events.details for flexible querying
- [ ] 14.4 Create V2__seed_default_rules.sql migration (default Drools rule definitions)
- [ ] 14.5 Verify migrations run correctly in dev (H2), staging (PostgreSQL), and test (Testcontainers)
- [ ] 14.6 Create rollback migrations for each schema change

## 15. Documentation

- [ ] 15.1 Document module structure and dependency DAG in README
- [ ] 15.2 Document SPI contracts with usage examples for each interface
- [ ] 15.3 Document webhook signature verification process for GitGuardian
- [ ] 15.4 Document credential rotation process (dual-write, verification, rollback)
- [ ] 15.5 Document deployment phases (POC → AWS Free Tier → ECS/RDS) with cost estimates
- [ ] 15.6 Document secrets management strategy (AWS SM + AES-256 fallback)
- [ ] 15.7 Document open questions from design.md and track resolution status
- [ ] 15.8 Add API documentation for all endpoints (request/response examples, error codes)
