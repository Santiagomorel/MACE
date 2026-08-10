# Proposal: Spring Boot Architecture

## Why

Los cambios de OpenSpec existentes (`alert-integrator`, `decision-engine-playbook-combination`, `logging-infrastructure`, `motor-verificacion-credenciales`, `action-executor-credential-rotation`) contienen decisiones tecnicas dispersas en 5 documents de diseño independientes. No existe un diseño de arquitectura unificada que responda como se organizan todos los modulos juntos: estructura del proyecto, limites de dependencias, comunicacion inter-modulo, estrategia de despliegue y gestion de credenciales admin del cliente (Punto 7). Esta propuesta consolida todas las decisiones en un solo change para desbloquear implementacion.

## What Changes

- **Estructura Maven Multi-Module:** Parent POM con 6 modulos en DAG lineal: `shared/models` → `shared/spi` → `alert-integrator` → `verification-engine` → `decision-engine` → `action-executor`
- **Contract Layer:** Interfaces en `shared/spi/` (SemVer) como unico mecanismo de comunicacion entre modulos. Cero importaciones cruzadas directas.
- **API REST v1:** Endpoints `/api/v1/` con versioning por URL, HMAC-SHA256 para webhooks, API key para admin, Bean Validation para requests
- **Cross-Cutting:** `@ControllerAdvice` para errores, Spring Actuator (health/metrics), config por profiles (dev/staging/prod)
- **Testing:** JUnit 5 + Mockito + AssertJ + Testcontainers (Postgres real) + JaCoCo (70% general, 80%+ dominio)
- **Secrets Híbrido:** AWS Secrets Manager (produccion) + PostgreSQL AES-256 encrypted columns (fallback/backup). Punto 7: credenciales admin del cliente protegidas en doble capa con rotation cada 90 dias
- **Deployment por Fases:** F1 POC $0 (Docker Compose + GitHub Actions) → F2 AWS Free Tier (12 meses) → F3 ECS/RDS ($57-97/mes)
- **Logging/Audit:** Logback JSON + MDC + secret redaction + audit_events table

## Capabilities

### New Capabilities
- `project-structure`: Maven multi-module architecture with parent POM, DAG dependency enforcement, contract layer via versioned SPI interfaces, package naming conventions
- `api-layer`: REST API v1 endpoints, URL versioning (`/api/v1/`), HMAC-SHA256 webhook signature validation, API key authentication for admin, CORS configuration, Bean Validation
- `alert-ingestion`: Webhook ingestion pipeline, provider adapter pattern (GitGuardian + SPI), event deduplication (Caffeine cache, 5min TTL), worker pool (5 concurrent, configurable), dead letter queue (DLQ via PostgreSQL)
- `credential-verification`: Verification engine with AWS STS provider, blast radius calculator, credential verifier, configurable severity rules per tenant
- `decision-engine`: Drools/KIE rules engine with per-tenant KieContainer hot-reload, YAML playbook manager, severity floor calculation (max of playbook floor + Drools result)
- `action-execution`: Rotation state machine (PENDING → ROTATING → SUCCESS/FAIL), AWS rotation services (AccessKey, IAMUser, RDS), notification dispatcher (Strategy pattern: Slack, Email, Ticket, SNS), credential rotation with dual-write and verification
- `secrets-management`: Hybrid secrets strategy — AWS Secrets Manager (primary) + PostgreSQL AES-256 encrypted columns (backup/encrypted storage), tenant credential management with double-layer protection, 90-day rotation policy, POC vs prod mode toggle
- `observability`: Structured logging (Logback JSON + MDC + secret redaction), Spring Actuator (health/readiness/liveness), Micrometer metrics with Prometheus endpoint, audit trail via `audit_events` table, 30-day log retention with auto-purge
- `testing`: Multi-layer testing strategy (unit, integration, web), Testcontainers PostgreSQL for integration tests, MockMvc for REST endpoints, JaCoCo coverage gates (70% general, 80%+ domain), Drools testing with KieFileSystem
- `deployment`: Docker multi-stage builds, docker-compose for local/staging, GitHub Actions CI/CD, multi-environment profiles (dev/staging/prod), blue-green deployment strategy, free-tier to production migration path

### Modified Capabilities
_(none — no existing specs in `openspec/specs/` to modify)_

## Impact

- **Code:** Creates entire project skeleton — parent POM, 6 modules, shared models + SPIs, all domain modules with package structure
- **Dependencies:** Maven multi-module build, Spring Boot 3.3.x, Java 21, PostgreSQL 16, Drools/KIE, AWS SDK v2, Testcontainers, JaCoCo, Docker
- **APIs:** `POST /api/v1/alerts`, `GET /api/v1/verification/{alertId}`, `POST /api/v1/decisions`, `POST /api/v1/actions`, `GET /api/v1/admin/rules`, `/actuator/health`
- **Database:** PostgreSQL schema with tables: `alerts`, `alert_dlq`, `verification_results`, `audit_events`, `client_credentials` (AES-256 encrypted)
- **Security:** HMAC-SHA256 webhook signing, API key for admin endpoints, IAM minimal permissions, AES-256 column encryption, secret redaction in logs
- **Infrastructure:** Docker Compose (local), GitHub Actions (CI/CD), AWS ECS/RDS/Secrets Manager (prod target)
