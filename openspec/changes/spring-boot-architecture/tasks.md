## 1. Parent POM and Module Scaffolding

- [x] [R1] 1.1 Create parent POM with Spring Boot 3.3.x parent, Java 21, and dependencyManagement BOM
- [x] [R1] 1.2 Define all 6 modules in parent POM: shared/models, shared/spi, alert-integrator, verification-engine, decision-engine, action-executor
- [x] [R1] 1.3 Configure dependencyManagement with versions: Spring Boot 3.3.x, AWS SDK v2, Drools/KIE, Testcontainers, JaCoCo
- [x] [R1] 1.4 Configure pluginManagement: maven-compiler-plugin (Java 21), maven-surefire-plugin, JaCoCo (70% general gate)
- [ ] [R1] 1.5 Verify `mvn clean package` builds all modules in topological order from parent directory

## 2. Shared Models

- [x] [R1] 2.1 Create `shared/shared-models` module with package `com.company.rotations.models`
- [x] [R1] 2.2 Implement `Alert` entity with JPA annotations (id, providerName, credentialType, tenantId, status, payload, rawPayload, receivedAt, state)
- [x] [R1] 2.3 Implement `AlertType` enum (AWS_ACCESS_KEY, IAM_USER, RDS_CREDENTIAL, GENERIC)
- [x] [R1] 2.4 Implement `AlertStatus` enum (PENDING, PROCESSING, VERIFIED, DECIDED, ROTATING, COMPLETED, FAILED, IN_DLQ)
- [x] [R1] 2.5 Implement `VerificationResult` entity (alertId, verified, reason, severityScope, blastRadius, credentialType, tenantId, provider, timestamp)
- [x] [R1] 2.6 Implement `DecisionOutput` model (alertId, decision: rotate/no_action/escalate, severity, reason, playbookName)
- [x] [R1] 2.7 Implement `RotationAction` entity (alertId, credentialType, status: PENDING/ROTATING/SUCCESS/FAIL/ESCALATE, provider, attempts, timeout, createdAt, updatedAt)
- [x] [R1] 2.8 Implement `AuditEvent` entity (eventType, tenantId, alertId, timestamp, details: JSONB, userId)
- [x] [R1] 2.9 Implement `AuditEventType` enum (ALERT_INGESTED, ALERT_DEDUPLICATED, CREDENTIAL_VERIFIED, CREDENTIAL_EXPIRED, ROTATION_STARTED, ROTATION_COMPLETED, ROTATION_FAILED, ESCALATION_TRIGGERED, CREDENTIAL_ACCESSED)
- [x] [R1] 2.10 Implement `GenericAlertModel` with all fields: eventId, source, sourceEventId, detectedSecret(type, valueHash, pattern), context(repository, file, commit, line, visibility, foundAt), providerSeverity, detectorState(isNew, previouslyFlagged, flagCount), receivedAt, rawPayload
- [x] [R1] 2.11 Write unit tests for all models (serialization/deserialization, enum values, toString masking for sensitive fields)

## 3. Shared SPIs

- [x] [R1] 3.1 Create `shared/shared-spi` module with package `com.company.rotations.spi`
- [x] [R1] 3.2 Implement `AlertAdapter` interface with version `1.0.0`: `GenericAlertModel toGenericAlert(Map<String, Object> rawPayload)`, `String getProviderName()`
- [x] [R1] 3.3 Implement `VerificationProvider` interface with version `1.0.0`: `VerificationResult verify(String credentialType, Map<String, String> credentials, String tenantId)`
- [x] [R1] 3.4 Implement `PlaybookManager` interface with version `1.0.0`: `Playbook loadPlaybook(String credentialType)`, `List<String> getPlaybookSteps(String credentialType)`
- [x] [R1] 3.5 Implement `RotationService` interface with version `1.0.0`: `RotationAction rotate(String credentialType, Map<String, String> credentials, String tenantId)`
- [x] [R1] 3.6 Implement `NotificationChannel` interface with version `1.0.0`: `void send(String message, Map<String, String> context)`
- [x] [R1] 3.7 Configure SPI module version as `1.0.0` in its POM
- [x] [R1] 3.8 Write unit tests for SPI interface contract validation (each interface has all required methods)

## 4. Alert Integrator Module

- [ ] [R2] 4.1 Create `alert-integrator` module with package `com.company.rotations.alerting`
- [ ] [R2] 4.2 Add POM dependencies to `shared/models` and `shared/spi`
- [ ] [R2] 4.3 Configure dependencies: Spring Boot web, Caffeine, PostgreSQL driver, Jackson
- [ ] [R2] 4.4 Create `AlertController` with `POST /api/v1/alerts` endpoint
- [ ] [R2] 4.5 Implement HMAC-SHA256 signature validation for webhook requests (`X-Signature` header)
- [ ] [R2] 4.6 Implement IP whitelist validation for webhook requests (configurable per provider)
- [ ] [R2] 4.7 Create `AdapterRegistry` singleton that maps provider names to AlertAdapter implementations
- [ ] [R2] 4.8 Implement `GitGuardianAdapter` that maps GitGuardian v2 API payload to GenericAlertModel
- [ ] [R2] 4.9 Implement `DefaultAdapter` as fallback for unknown sources
- [ ] [R2] 4.10 Create `EventDedupService` using Caffeine cache (key: sourceEventId, TTL: configurable, default 5min)
- [ ] [R2] 4.11 Create `SecretDedupService` using Caffeine + DB (key: valueHash + repository, state-based cooldowns)
- [ ] [R2] 4.12 Create `WorkerPool` with configurable size (default 5) and bounded queue (default 1000)
- [ ] [R2] 4.13 Implement backpressure: return HTTP 429 when queue is full
- [ ] [R2] 4.14 Create `alert_dlq` table and `DeadLetterQueueService`
- [ ] [R2] 4.15 Integrate DLQ into processing pipeline (insert on failures)
- [ ] [R2] 4.16 Implement DLQ cleanup scheduled task (delete entries older than 7 days)
- [ ] [R2] 4.17 Create `WebhookPipeline` orchestrating: validate auth → event dedup → adapter → secret dedup → worker queue
- [ ] [R2] 4.18 Write unit tests for AlertController (valid payload, invalid signature, missing signature, invalid IP, missing required fields)
- [ ] [R2] 4.19 Write unit tests for GitGuardianAdapter (full mapping, partial mapping, missing optional fields)
- [ ] [R2] 4.20 Write unit tests for AdapterRegistry (found adapter, missing adapter → NotFoundException)
- [ ] [R2] 4.21 Write unit tests for EventDedupService (hit, miss, TTL expiration, concurrent access)
- [ ] [R2] 4.22 Write unit tests for SecretDedupService (all state transitions, cooldown periods)
- [ ] [R2] 4.23 Write unit tests for WorkerPool (concurrent processing, backpressure, worker failure)
- [ ] [R2] 4.24 Write integration test for end-to-end webhook flow with mocked adapter and cache
- [ ] [R2] 4.25 Write integration test for DLQ flow (invalid payload → DLQ entry)
- [ ] [R2] 4.26 Add sample webhook payloads in `src/test/resources/webhooks/` for GitGuardian

## 5. Verification Engine Module

- [ ] [R2] 5.1 Create `verification-engine` module with package `com.company.rotations.verification`
- [ ] [R2] 5.2 Add POM dependencies to `shared/models`, `shared/spi`, and `alert-integrator`
- [ ] [R2] 5.3 Configure dependencies: AWS SDK v2 (STS), Spring Boot
- [ ] [R2] 5.4 Implement `AwsStsVerificationProvider` that calls AWS STS GetCallerIdentity
- [ ] [R2] 5.5 Implement `BlastRadiusCalculator` that evaluates credential policies to determine scope
- [ ] [R2] 5.6 Implement `SeverityRuleEngine` that applies per-tenant severity floors
- [ ] [R2] 5.7 Implement `VerificationService` orchestrating provider call + blast radius + severity rules
- [ ] [R2] 5.8 Create verification result repository for storing VerificationResult
- [ ] [R2] 5.9 Write unit tests for AwsStsVerificationProvider (active key, expired key, invalid key, provider unavailable)
- [ ] [R2] 5.10 Write unit tests for BlastRadiusCalculator (AdministratorAccess → critical, ReadOnlyAccess → low, custom policies)
- [ ] [R2] 5.11 Write unit tests for SeverityRuleEngine (tenant floor higher than calculated, tenant floor lower, no tenant rules → default)
- [ ] [R2] 5.12 Write integration tests with Testcontainers for verification result persistence

## 6. Decision Engine Module

- [ ] [R2] 6.1 Create `decision-engine` module with package `com.company.rotations.decision`
- [ ] [R2] 6.2 Add POM dependencies to `shared/models`, `shared/spi`, and `verification-engine`
- [ ] [R2] 6.3 Configure dependencies: Drools/KIE (`kie-spring`, `drools-core`), Spring Boot
- [ ] [R2] 6.4 Create default Drools rules in `src/main/resources/rules/` (evaluate verified=true+high→rotate, verified=false→no_action)
- [ ] [R2] 6.5 Implement `DefaultKieContainerProvider` that loads rules from classpath on startup
- [ ] [R2] 6.6 Implement `TenantKieContainerProvider` that maintains per-tenant KieContainer with hot-reload capability
- [ ] [R2] 6.7 Implement `PlaybookManager` that loads YAML playbooks from `src/main/resources/playbooks/`
- [ ] [R2] 6.8 Implement `RuleEngineService` that combines: Drools evaluation → severity floor → DecisionOutput
- [ ] [R2] 6.9 Create admin endpoint `GET /api/v1/admin/rules` for rule management (requires API key)
- [ ] [R2] 6.10 Write unit tests for RuleEngineService (rotate decision, no_action decision, severity floor logic)
- [ ] [R2] 6.11 Write Drools tests with KieFileSystem for dynamic rule loading
- [ ] [R2] 6.12 Write unit tests for PlaybookManager (load by credential type, missing playbook → exception)

## 7. Action Executor Module

- [ ] [R3] 7.1 Create `action-executor` module with package `com.company.rotations.actions`
- [ ] [R3] 7.2 Add POM dependencies to `shared/models`, `shared/spi`, and `decision-engine`
- [ ] [R3] 7.3 Configure dependencies: AWS SDK v2 (IAM, STS, RDS), Spring Boot, Slack SDK
- [ ] [R3] 7.4 Implement `RotationStateMachine` managing transitions: PENDING → ROTATING → SUCCESS|FAIL → ESCALATE
- [ ] [R3] 7.5 Implement `AwsRotationService` for AccessKey rotation (set_inactive → wait → create → verify)
- [ ] [R3] 7.6 Implement `AwsRotationService` for IAMUser rotation (deactivate → delete → create → verify)
- [ ] [R3] 7.7 Implement notification channels — see `action-executor-credential-rotation` tasks Section 3 for detailed tasks

## 8. Secrets Management (continued)

- [ ] [R3] 8.1 Implement `SecretVaultService` with AWS Secrets Manager client (prod/staging profile)
- [ ] [R3] 8.2 Implement `SecretVaultService` fallback to PostgreSQL encrypted columns (dev/test profile)
- [ ] [R3] 8.3 Implement `Aes256Converter` JPA AttributeConverter for column encryption
- [ ] [R3] 8.4 Implement `SecretRedactingConverter` custom Logback converter for secret pattern redaction — see `logging-infrastructure` tasks Section 3
- [ ] [R3] 8.5 Create `client_credentials` table with AES-256 encrypted columns (access_key_encrypted, secret_key_encrypted)
- [ ] [R3] 8.6 Implement `ClientCredentialRepository` for DB credential storage and retrieval
- [ ] [R3] 8.7 Implement credential retrieval with fallback: AWS SM primary → DB backup → error
- [ ] [R3] 8.8 Implement secret path convention: `/app/rotation/{path}` and `/clients/{tenantId}/{secret-type}`
- [ ] [R3] 8.9 Implement rotation policy tracking: rotated_at timestamp, 90-day configurable deadline, alert triggers
- [ ] [R3] 8.10 Configure `ENCRYPTION_MASTER_KEY` env var loading for POC mode
- [ ] [R3] 8.11 Write unit tests for Aes256Converter (encrypt/decrypt roundtrip, key loading)
- [ ] [R3] 8.12 Write unit tests for SecretVaultService (AWS SM path, DB fallback, path convention)
- [ ] [R3] 8.13 Write unit tests for SecretRedactingConverter (AWS key pattern, IAM pattern, non-matching text)
- [ ] [R3] 8.14 Write integration tests for client_credentials table with encrypted columns

## 9. Observability

> **Note:** Detailed logging implementation tasks are in `logging-infrastructure` change. This section covers integration and configuration only.

- [ ] [R2] 9.1 Integrate Logback JSON layout — see `logging-infrastructure` tasks Section 1
- [ ] [R2] 9.2 Configure MDC context propagation (tenantId, alertId, sessionId) via `OncePerRequestFilter` — see `logging-infrastructure` tasks Section 2
- [ ] [R2] 9.3 Add `SecretRedactingConverter` to Logback configuration — see `logging-infrastructure` tasks Section 3
- [ ] [R2] 9.4 Configure Spring Actuator endpoints: health, readiness, liveness, metrics, prometheus
- [ ] [R2] 9.5 Implement custom health indicator for Secrets Manager connectivity
- [ ] [R2] 9.6 Implement custom health indicator for Database connectivity
- [ ] [R2] 9.7 Configure Micrometer metrics: HTTP request metrics (duration, status, method)
- [ ] [R2] 9.8 Configure domain-specific Micrometer metrics: alerts.ingested, alerts.deduplicated, verification.completed, rotation.completed, rotation.failed
- [ ] [R2] 9.9 Integrate `AuditEventService` — see `logging-infrastructure` tasks Section 5
- [ ] [R2] 9.10 Integrate `AuditEventRepository` — see `logging-infrastructure` tasks Section 4
- [ ] [R2] 9.11 Implement scheduled audit event purger (delete events older than 90 days) — see `logging-infrastructure` tasks Section 6
- [ ] [R2] 9.12 Configure actuator `show-details=when-authorized` for prod profile
- [ ] [R2] 9.13 Write unit tests for AuditEventService — see `logging-infrastructure` tasks Section 5
- [ ] [R2] 9.14 Write integration tests for audit event persistence with Testcontainers — see `logging-infrastructure` tasks Section 5

## 10. Cross-Cutting Concerns

- [x] [R1] 10.1 Create `@ControllerAdvice` with `@ExceptionHandler` for global error handling
- [x] [R1] 10.2 Implement `ErrorResponse` model (timestamp, status, error, path, message, details)
- [x] [R1] 10.3 Implement `TechnicalExceptionHandler` returning HTTP 500 without stack traces
- [x] [R1] 10.4 Implement `BadRequestExceptionHandler` returning HTTP 400 with validation details
- [x] [R1] 10.5 Implement URL versioning: all API endpoints under `/api/v1/`
- [x] [R1] 10.6 Implement `ApiKeyInterceptor` for `/api/v1/admin/*` endpoints (X-API-Key header)
- [x] [R1] 10.7 Implement CORS configuration: `*` allowed in dev, restricted origins in prod
- [x] [R1] 10.8 Implement Bean Validation on request bodies (@Valid, @NotBlank, @EnumValue)
- [x] [R1] 10.9 Create `GlobalExceptionHandler` unit tests (business error, technical error, validation error)
- [x] [R1] 10.10 Create `ApiKeyInterceptor` unit tests (valid key, missing key, invalid key)

## 11. Environment Profiles

- [ ] [R3] 11.1 Create `application.yml` with common configuration
- [ ] [R3] 11.2 Create `application-dev.yml` with H2 in-memory, DEBUG logging, create-drop DDL
- [ ] [R3] 11.3 Create `application-staging.yml` with PostgreSQL, validate DDL, INFO logging
- [ ] [R3] 11.4 Create `application-prod.yml` with PostgreSQL, validate DDL, WARN framework/INFO app logging, actuator when-authorized
- [ ] [R3] 11.5 Create `application-test.yml` with Testcontainers PostgreSQL, create-drop DDL
- [ ] [R3] 11.6 Configure `spring.profiles.active` via Maven property (overridable at build time)
- [ ] [R3] 11.7 Write integration tests verifying profile-specific configuration (each profile loads correctly)

## 12. Testing Infrastructure

- [ ] [R3] 12.1 Configure JaCoCo Maven plugin with coverage gates: 70% general, 80%+ domain
- [ ] [R3] 12.2 Configure per-type coverage targets: controllers/adapters 60%, models/exceptions 90%
- [ ] [R3] 12.3 Add Testcontainers dependency to parent POM (`testcontainers-postgresql`)
- [ ] [R3] 12.4 Create base integration test class `@Testcontainers @ActiveProfiles("test")` for all modules
- [ ] [R3] 12.5 Create `@WebMvcTest` base class for controller tests
- [ ] [R3] 12.6 Configure Drools test utilities: `KieFileSystem` helper, `KieContainer` builder
- [ ] [R3] 12.7 Verify JaCoCo coverage gates fail build when thresholds not met
- [ ] [R3] 12.8 Add excluded dependencies: `junit-vintage-engine` (JUnit 4 only, no vintage)

## 13. Docker and Deployment

- [ ] [R3] 13.1 Create Dockerfile with multi-stage build (Maven 3.9 + Java 21 build stage, eclipse-temurin:21-jre-alpine runtime)
- [ ] [R3] 13.2 Configure Dockerfile to run as non-root user in runtime stage
- [ ] [R3] 13.3 Create `docker-compose.yml` with Spring Boot app + PostgreSQL 16 + health checks
- [ ] [R3] 13.4 Configure PostgreSQL dependency health check in docker-compose
- [ ] [R3] 13.5 Configure application health check against `/actuator/health` in docker-compose
- [ ] [R3] 13.6 Add `.env.example` template with required environment variables
- [ ] [R3] 13.7 Add `.gitignore` entries for `.env.*` files and secrets
- [ ] [R3] 13.8 Create GitHub Actions workflow for PR builds (`mvn clean verify`)
- [ ] [R3] 13.9 Create GitHub Actions workflow for main branch deployment (build Docker → push to registry → deploy)
- [ ] [R3] 13.10 Configure CI coverage gate: fail if JaCoCo thresholds not met
- [ ] [R3] 13.11 Create blue-green deployment documentation for Phase 3

## 14. Database Migrations

- [ ] [R3] 14.1 Create V1__init_schema.sql migration (alerts, alert_dlq, verification_results, audit_events, client_credentials, rotation_actions tables)
- [ ] [R3] 14.2 Add indexes on key columns: alerts(tenantId, status), verification_results(alertId), audit_events(tenantId, eventType, timestamp), client_credentials(tenantId), rotation_actions(alertId)
- [ ] [R3] 14.3 Add JSONB GIN index on audit_events.details for flexible querying
- [ ] [R3] 14.4 Create V2__seed_default_rules.sql migration (default Drools rule definitions)
- [ ] [R3] 14.5 Verify migrations run correctly in dev (H2), staging (PostgreSQL), and test (Testcontainers)
- [ ] [R3] 14.6 Create rollback migrations for each schema change

## 15. Documentation

- [ ] [R3] 15.1 Document module structure and dependency DAG in README
- [ ] [R3] 15.2 Document SPI contracts with usage examples for each interface
- [ ] [R3] 15.3 Document webhook signature verification process for GitGuardian
- [ ] [R3] 15.4 Document credential rotation process (dual-write, verification, rollback)
- [ ] [R3] 15.5 Document deployment phases (POC → AWS Free Tier → ECS/RDS) with cost estimates
- [ ] [R3] 15.6 Document secrets management strategy (AWS SM + AES-256 fallback)
- [ ] [R3] 15.7 Document open questions from design.md and track resolution status
- [ ] [R3] 15.8 Add API documentation for all endpoints (request/response examples, error codes)
