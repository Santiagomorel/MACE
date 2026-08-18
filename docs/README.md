# Credential Rotation System — Multi-Module Project

## Overview

The **Credential Rotation System** is a Spring Boot multi-module Maven project that automates the detection, verification, decision-making, and rotation of exposed cloud credentials (AWS access keys, IAM users, etc.) from security scanners like GitGuardian.

The system follows a **linear dependency DAG** with six modules. Communication between modules is strictly decoupled via SPI interfaces in `shared/spi`.

## Architecture

```
shared/models → shared/spi → alert-integrator → verification-engine → decision-engine → action-executor
```

```
┌─────────────────────────────────────────────────────────────────────┐
│                          Ingestion Layer                           │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                   alert-integrator                           │  │
│  │  Webhook → Dedup → Adapter → Worker Pool → DLQ               │  │
│  └──────────────────────────────────────────────────────────────┘  │
└───────────────────────────┬─────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        Verification Layer                          │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                verification-engine                            │  │
│  │  AWS STS → Blast Radius → Severity Rules                     │  │
│  └──────────────────────────────────────────────────────────────┘  │
└───────────────────────────┬─────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        Decision Layer                              │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                   decision-engine                             │  │
│  │  Drools Rules + Playbooks → Rotate / No Action / Escalate    │  │
│  └──────────────────────────────────────────────────────────────┘  │
└───────────────────────────┬─────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│                       Execution Layer                              │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                   action-executor                             │  │
│  │  State Machine → AWS IAM/RDS → Notification Dispatcher       │  │
│  └──────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

## Modules

### `shared/shared-models`

Domain entities shared across all modules.

| Entity | Table | Description |
|--------|-------|-------------|
| `Alert` | `alerts` | Represents a credential exposure alert from any provider |
| `AlertType` | enum | `AWS_ACCESS_KEY`, `IAM_USER`, `RDS_CREDENTIAL`, `GENERIC` |
| `AlertStatus` | enum | `PENDING`, `PROCESSING`, `VERIFIED`, `DECIDED`, `ROTATING`, `COMPLETED`, `FAILED`, `IN_DLQ` |
| `VerificationResult` | `verification_results` | Result of credential verification (verified/not verified, blast radius) |
| `RotationAction` | `rotation_actions` | Tracks a credential rotation operation |
| `AuditEvent` | `audit_events` | Immutable audit trail for all system events |
| `Credential` | `credentials` | Admin credentials for a tenant (used by verification-engine) |
| `GenericAlertModel` | N/A | Generic alert representation normalized from any provider |

**Package:** `com.company.rotations.models`

### `shared/shared-spi`

Service Provider Interfaces that decouple inter-module communication.

| SPI | Version | Methods | Provider | Consumer |
|-----|---------|---------|----------|----------|
| `AlertAdapter` | 1.0.0 | `toGenericAlert()`, `getProviderName()` | alert-integrator | verification-engine |
| `VerificationProvider` | 1.0.0 | `verify()` | verification-engine | decision-engine |
| `DecisionEngine` | — | `evaluate()` | decision-engine | action-executor |
| `PlaybookManager` | 1.0.0 | `loadPlaybook()`, `getPlaybookSteps()`, `getSeverityFloor()` | decision-engine | action-executor |
| `RotationService` | 1.0.0 | `rotate()` | action-executor | external |
| `NotificationChannel` | 1.0.0 | `send()` | action-executor | external |

**Package:** `com.company.rotations.spi`

**Versioning policy:**
- Breaking changes (method removed, signature changed): advance major version (1.x → 2.0)
- Backward-compatible changes (new default methods): advance minor version (1.x → 1.y)

### `alert-integrator`

Receives webhook alerts from security scanners, normalizes them, deduplicates, and dispatches to workers.

**Key components:**
- `WebhookController` — `POST /api/v1/alerts` endpoint
- `AdapterRegistry` — Maps provider names to `AlertAdapter` implementations
- `GitGuardianAdapter` — Maps GitGuardian API v2 payload to `GenericAlertModel`
- `EventDedupService` — Caffeine-based dedup (5 min TTL by `sourceEventId`)
- `SecretDedupService` — State-based dedup (cooldowns driven by verification results)
- `WorkerPool` — Fixed-size thread pool (default 5) with bounded queue (1000)
- `DeadLetterQueueService` — PostgreSQL-backed DLQ with cleanup

**Package:** `com.company.rotations.alerting`

### `verification-engine`

Verifies exposed credentials against cloud provider APIs and calculates blast radius.

**Key components:**
- `AwsStsVerificationProvider` — Calls AWS STS `GetCallerIdentity` to verify credentials
- `BlastRadiusCalculator` — Evaluates credential policies to determine scope
- `SeverityRuleEngine` — Applies per-tenant severity floors

**Package:** `com.company.rotations.verification`

### `decision-engine`

Evaluates verification results against Drools rules and YAML playbooks to decide the action.

**Key components:**
- `DefaultKieContainerProvider` — Loads default Drools rules from classpath
- `TenantKieContainerProvider` — Per-tenant `KieContainer` with hot-reload
- `PlaybookManager` — Loads YAML playbooks from `src/main/resources/playbooks/`
- `RuleEngineService` — Combines Drools evaluation + severity floor → decision

**Package:** `com.company.rotations.decision`

### `action-executor`

Executes credential rotation and sends notifications based on decisions.

**Key components:**
- `RotationStateMachine` — `PENDING → ROTATING → SUCCESS | FAIL → ESCALATE`
- `AwsRotationService` — AWS IAM/RDS credential rotation (set_inactive → create → verify)
- `SecretVaultService` — Hybrid secrets storage (AWS SM + PostgreSQL AES-256)
- `NotificationDispatcher` — Strategy pattern for Slack, Email, Ticket, SNS

**Package:** `com.company.rotations.actions`

## Building

```bash
# Build all modules
mvn clean package

# Build a specific module only
mvn -pl alert-integrator clean package

# Build module with dependencies
mvn -pl verification-engine -am clean package
```

## Running

```bash
# Run with dev profile (H2 in-memory, create-drop DDL)
mvn spring-boot:run -pl alert-integrator -Dspring-boot.run.profiles=dev

# Run all modules via Docker Compose
docker-compose up
```

## Configuration Profiles

| Profile | Database | Flyway | DDL Auto | Logging |
|---------|----------|--------|----------|---------|
| `dev` | H2 in-memory | Disabled | `create-drop` | DEBUG |
| `staging` | PostgreSQL | Enabled | `validate` | INFO |
| `prod` | PostgreSQL | Enabled | `validate` | WARN (framework) / INFO (app) |
| `test` | PostgreSQL (Testcontainers) | Enabled | `create-drop` | INFO |

Set via `SPRING_PROFILES_ACTIVE` env var or Maven property.

## Database Migrations

Each module manages its own Flyway migrations in `src/main/resources/db/migration/`:

| Module | Migrations |
|--------|------------|
| `alert-integrator` | `V1__alert_dlq.sql`, `V2__alerts.sql`, `V3__audit_events.sql` |
| `verification-engine` | `V1__verification_results.sql` |
| `decision-engine` | `V1__playbooks.sql`, `V2__credentials.sql`, `V3__client_rules.sql`, `V4__default_rules.sql` |
| `action-executor` | `V1__rotation_actions.sql` |

Rollback scripts: `R__undo-V*.sql` in each module.

## Observability

- **Structured logging:** Logback JSON format (compatible with Datadog, CloudWatch, ELK)
- **MDC context:** `tenantId`, `alertId`, `sessionId` propagated across all log lines
- **Secret redaction:** Logback converter masks `AKIA*` patterns with `***REDACTED***`
- **Spring Actuator:** `/actuator/health`, `/actuator/health/readiness`, `/actuator/health/liveness`
- **Micrometer metrics:** Prometheus format at `/actuator/prometheus`
- **Audit trail:** `audit_events` table with 30-day retention

## Module Dependency Graph

```
shared/shared-models (no internal deps)
       │
       ▼
shared/shared-spi (depends on shared-models)
       │
       ▼
alert-integrator (depends on shared-models, shared-spi)
       │
       ▼
verification-engine (depends on shared-models, shared-spi, alert-integrator)
       │
       ▼
decision-engine (depends on shared-models, shared-spi, verification-engine)
       │
       ▼
action-executor (depends on shared-models, shared-spi, decision-engine)
```

Dependencies are enforced at compile time via Maven POM declarations. If a module imports a package from a non-declared module, compilation fails.
