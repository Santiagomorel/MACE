# Guia de Implementacion por Releases

> Base: `proyecto_estructurado.txt`
> Estrategia: Seguir orden R1→R2→R3 definido en el documento. Decision engine como prioridad (mayor peso). Testing con mockeo hasta tener pipeline real.

---

## Resumen de Releases

| Release   | Cierre   | Enfoque                              | Cambios OpenSpec implicados                          | Tareas estimadas |
|-----------|----------|--------------------------------------|------------------------------------------------------|-----------------|
| **R1**    | 27/08    | Playbooks + Analisis + Criticidad   | spring-boot-arch (foundation) + decision-engine      | ~80             |
| **R2**    | 24/09    | Webhooks + Analisis Cloud           | alert-integrator + motor-verificacion-credenciales   | ~107            |
| **R3**    | 05/11    | Acciones + Rotacion + Dashboard     | action-executor-credential-rotation + polish         | ~100            |
| **Trans** | Todas    | Observabilidad + Arquitectura       | logging-infrastructure + spring-boot-arch (polish)   | ~226            |

---

## Pipeline del Sistema

```
┌──────────────┐    ┌──────────────────┐    ┌──────────────────┐    ┌──────────────┐
│  Webhook     │    │  Verificacion    │    │  Decision Engine │    │  Action      │
│  Ingestion   │───▶│  AWS STS/Azure   │───▶│  Drools+Playbook │───▶│  Rotation    │
│              │    │  GCP             │    │  Severity+Blast  │    │  Notification│
└──────────────┘    └──────────────────┘    └──────────────────┘    └──────────────┘
        ↑                    ↑                       ↑                     ↑
       R2                   R2                     R1                   R3
```

**Nota critica:** El decision engine (R1) es el modulo de mayor peso. Para R1, recibe un `VerificationResult` mockeado como input — no requiere webhooks ni verificacion real para funcionar.

---

## Estrategia de Mockeo

En R1, el decision engine se prueba con payloads estaticos/mockeados:

```
DecisionEngineService  ← VerificationResult (mock)
                            ↓
                      DecisionOutput (real)
```

Esto es valido porque:
1. El decision engine es independiente del input — toma un `VerificationResult` y produce un `DecisionOutput`
2. Se puede testear con datos mockeados via MockMvc y JUnit
3. El documento define R1 como "Playbooks + Motor de Analisis" — logicamente es puro decision engine

---

# RELEASE 1 — Cierre: 27/08

## Enfoque: Playbooks, Motor de Analisis y Criticidad

### Objetivo

Sistema capaz de evaluar la criticidad de credenciales expuestas usando:
- **Playbooks YAML** — base de decisiones del sistema (peso menor)
- **Reglas Drools configurables por cliente** — peso mayor en la ecuacion de riesgo
- **Calculo de blast radius** — identidades expuestas y su alcance
- **Criterio de severidad** — combinacion playbook + reglas cliente

### Input (mock para R1)

```java
VerificationResult mock = VerificationResult.builder()
    .alertId("test-001")
    .verified(true)
    .credentialType("AWS_ACCESS_KEY")
    .blastRadius("AdministratorAccess")
    .actionMatrix(Set.of("s3_full_access", "iam_modify"))
    .severityScope(Severidad.ALTO)
    .tenantId("tenant-1")
    .build();
```

### Cambios OpenSpec — Tareas por Release

#### A. spring-boot-architecture (Foundation)

**Tasks a implementar en R1:**

| Section | Tasks | Descripcion |
|---------|-------|-------------|
| 1. Parent POM | 1.1-1.5 | Spring Boot 3.3.x, Java 21, parent POM con 6 modulos |
| 2. Shared Models | 2.1-2.11 | Alert, AlertType, AlertStatus, VerificationResult, DecisionOutput, RotationAction, AuditEvent, GenericAlertModel |
| 3. Shared SPIs | 3.1-3.8 | AlertAdapter, VerificationProvider, PlaybookManager, RotationService, NotificationChannel |
| 10. Cross-Cutting | 10.1-10.10 | @ControllerAdvice, error handling, API versioning `/api/v1/`, API key auth |

**Total tasks R1 foundation:** ~38

**Tasks pospuestos a R2/R3:**
- Section 4 (alert-integrator module) → R2
- Section 5 (verification-engine) → R2
- Section 6 (decision-engine module) → R1 (solo scaffolding, logica real en decision-engine change)
- Section 7 (action-executor) → R3
- Section 8 (secrets management) → R3
- Section 9 (observability integration) → R2
- Section 12-15 (testing infra, Docker, deployment, docs) → R3

#### B. decision-engine-playbook-combination (Core R1)

**Tasks a implementar en R1:**

| Section | Tasks | Descripcion |
|---------|-------|-------------|
| 1. Playbook Schema | 1.1-1.6 | Schema Playbook YAML, loader, DB playbooks con versionado |
| 2. Severity Calculation | 2.1-2.7 | Formula `max(playbook_floor, reglas_cliente)`, enum Severidad |
| 3. Drools Integration | 3.1-3.6 | KieFileSystem, .drl hot-reload, client_rules table, LRU cache |
| 4. Rule Validation | 4.1-4.3 | Validacion de DRL, rollback automatico, audit version |
| 5. Conflict Resolution | 5.1-5.3 | Saliencia Drools, priority-based agenda |
| 6. Alert Versioning | 6.1-6.3 | Versionado de reglas, alerta en vuelo no se recalcula |

**Total tasks decision-engine R1:** ~28

**Tasks pospuestos:**
- Section 7 (AWS Metadata Discovery) → R2
- Section 8+ (Rules Update Mechanisms) → R2

#### C. logging-infrastructure (Basico)

**Tasks a implementar en R1:**

| Section | Tasks | Descripcion |
|---------|-------|-------------|
| 1. Module Setup | 1.1-1.7 | Package organization, Logback JSON, async appender |
| 2. MDC | 2.1-2.7 | OncePerRequestFilter, trace_id, alert_id, client_id |

**Total tasks logging R1:** ~14

**Tasks pospuestos a R2:**
- Section 3 (Secret Redaction) → R2
- Section 4-6 (Audit trail, purge) → R2
- Section 7-8 (Metrics, Prometheus) → R2

### Deliverables R1

- [ ] Parent POM con 6 modulos compilando
- [ ] Shared models completos (15+ entidades)
- [ ] Shared SPIs (5 interfaces + tests)
- [ ] Decision engine: Playbooks YAML + Drools + severity calculation
- [ ] Admin endpoint: `GET /api/v1/admin/rules` (gestion de reglas Drools)
- [ ] Logging basico: JSON estructurado + MDC fields
- [ ] Tests unitarios: Decision engine con payloads mockeados
- [ ] REST endpoints de decision engine con MockMvc

---

# RELEASE 2 — Cierre: 24/09

## Enfoque: Integracion y Analisis Cloud

### Objetivo

Pipeline completo hasta decision: webhook → normalizacion → verificacion AWS STS → decision (R1).

### Cambios OpenSpec — Tareas por Release

#### A. alert-integrator (Core R2)

**Tasks a implementar en R2:**

| Section | Tasks | Descripcion |
|---------|-------|-------------|
| 1. Module Setup | 1.1-1.7 | Package, Caffeine, endpoint `/api/v1/alerts` |
| 2. Webhook Endpoint | 2.1-2.7 | Controller, HMAC-SHA256, IP whitelist, tests |
| 3. Adapter Pattern | 3.1-3.5 | GitGuardianAdapter, DefaultAdapter, AdapterRegistry |
| 4. Event Dedup | 4.1-4.4 | Caffeine cache, event dedup integration |
| 5. Secret Dedup | 5.1-5.3 | State-based dedup, cooldowns, verifier integration |
| 6. Worker Pool | 6.1-6.6 | Configurable pool, concurrent processing |
| 7. DLQ | 7.1-7.4 | Dead letter queue table, service |
| 8. Pipeline Integration | 8.1-8.4 | End-to-end pipeline orchestration |
| 9. Verifier Integration | 9.1-9.6 | GenericAlertModel input, output format |
| 10-11. Tests | 10.1-11.7 | Unit + integration tests |

**Total tasks alert-integrator R2:** ~65

#### B. motor-verificacion-credenciales (Core R2)

**Tasks a implementar en R2:**

| Section | Tasks | Descripcion |
|---------|-------|-------------|
| 1. Module Setup | 1.1-1.5 | Package, AWS SDK v2, circuit breakers, cache |
| 2. Provider Detection | 2.1-2.5 | AWS/Azure/GCP detection by prefix |
| 3. Verification | 3.1-3.6 | AWS STS GetCallerIdentity, verification service |
| 4. Permission Enum | 4.1-4.6 | Action-permission matrix |
| 5. Account Mapping | 5.1-5.5 | Hint-based + fallback |
| 6. Cache & CB | 6.1-6.4 | Result cache 5min, circuit breakers |
| 7. Tests | 7.1-7.5 | Unit + integration with Testcontainers |

**Total tasks motor-verificacion R2:** ~42

#### C. decision-engine — Continuation R2

**Tasks adicionales de R1:**

| Section | Tasks | Descripcion |
|---------|-------|-------------|
| 7. AWS Metadata | 7.1-7.4 | Metadata discovery service (R1 tenia solo decision logic) |
| 8. Rules Update | 8.1-8.5 | Mechanisms for rule updates |

**Total tasks decision-engine continuation R2:** ~12

#### D. logging-infrastructure — Continuation R2

**Tasks adicionales:**

| Section | Tasks | Descripcion |
|---------|-------|-------------|
| 3. Secret Redaction | 3.1-3.5 | Logback converter |
| 4. Audit Event Model | 4.1-4.5 | AuditEvent entity, repository, migration |
| 5. Audit Service | 5.1-5.4 | Sync audit writes for all pipeline events |
| 6. Audit Purge | 6.1-6.4 | Scheduled purge at 90 days |
| 7-8. Metrics | 7.1-8.5 | Micrometer metrics, Prometheus endpoint |

**Total tasks logging continuation R2:** ~29

#### E. spring-boot-architecture — Continuation R2

**Tasks adicionales:**
- Section 9 (observability integration) — Micrometer, Prometheus
- Section 4 (alert-integrator module scaffolding)

### Deliverables R2

- [ ] Webhook endpoint `/api/v1/alerts` con GitGuardian
- [ ] Dedup event + secret level (Caffeine)
- [ ] Worker pool con backpressure
- [ ] DLQ para alerts fallidos
- [ ] AWS STS verification con circuit breakers
- [ ] Playbook combination en pipeline real (no mock)
- [ ] Audit trail completo en `audit_events`
- [ ] Metrics Prometheus
- [ ] Tests integration: webhook → verification → decision

---

# RELEASE 3 — Cierre: 05/11

## Enfoque: Alertas y Toma de Acciones

### Objetivo

Pipeline end-to-end: webhook → verification → decision → rotation → notification.

### Cambios OpenSpec — Tareas por Release

#### A. action-executor-credential-rotation (Core R3)

**Tasks a implementar en R3:**

| Section | Tasks | Descripcion |
|---------|-------|-------------|
| 1. State Machine | 1.1-1.5 | RotationState enum, transitions, timeout, retry |
| 2. AWS STS Flow | 2.1-2.7 | UpdateAccessKey → CreateAccessKey → store |
| 3. Notification | 3.1-3.7 | Slack, Email, Ticket, SNS + dispatcher |
| 4. Audit Trail | 4.1-4.4 | State machine transitions logging |
| 5. Integration | 5.1-5.5 | Wire state machine + rotation + notification |
| 6. Tests | 6.1-6.7 | Unit + integration end-to-end |

**Total tasks action-executor R3:** ~35

#### B. spring-boot-architecture — Polish R3

**Tasks a implementar en R3:**

| Section | Tasks | Descripcion |
|---------|-------|-------------|
| 8. Secrets Mgmt | 8.1-8.14 | AWS SM + PostgreSQL AES-256 fallback |
| 11. Profiles | 11.1-11.7 | Dev/staging/prod profiles |
| 12. Testing Infra | 12.1-12.8 | JaCoCo, Testcontainers, coverage gates |
| 13. Docker Deploy | 13.1-13.11 | Dockerfile, docker-compose, CI/CD |
| 14. DB Migrations | 14.1-14.4 | Flyway migration orchestration |
| 15. Docs | 15.1-15.5 | README, architecture, deployment guide |

**Total tasks spring-boot-arch polish R3:** ~53

### Deliverables R3

- [ ] Pipeline end-to-end completo
- [ ] Rotacion automatica de credenciales AWS
- [ ] Notificaciones (Slack, Email, Ticket, SNS)
- [ ] State machine de rotacion con audit trail
- [ ] Secrets management (AWS SM + PostgreSQL AES-256)
- [ ] Docker + docker-compose
- [ ] Testing infra con coverage gates
- [ ] Documentacion completa
- [ ] Admin dashboard (GUI)

---

# Cierre del Proyecto

- Pruebas integrales finales
- Documentacion del sistema
- Manuales de usuario y operacion
- Hardenizacion de ambiente de produccion
- Handover al equipo del cliente

---

## Resumen de Tareas por Release

| Release | Foundation | Decision Engine | Alert Integrator | Verification | Action Executor | Logging | Arch Polish | **Total** |
|---------|-----------|-----------------|------------------|-------------|-----------------|---------|-------------|-----------|
| R1      | ~38       | ~28             | 0                | 0           | 0               | ~14     | 0           | **~80**   |
| R2      | ~5        | ~12             | ~65              | ~42         | 0               | ~29     | 0           | **~153**  |
| R3      | 0         | 0               | 0                | 0           | ~35             | 0       | ~53         | **~88**   |

---

## Orden de Implementacion Recomendado por Sprint

### R1 — Sprint 1 (Foundation + Decision)

1. spring-boot-arch: Parent POM (tasks 1.1-1.5)
2. spring-boot-arch: Shared Models (tasks 2.1-2.11)
3. spring-boot-arch: Shared SPIs (tasks 3.1-3.8)
4. spring-boot-arch: Cross-Cutting (tasks 10.1-10.10)
5. logging: Module Setup (tasks 1.1-1.7)
6. logging: MDC (tasks 2.1-2.7)
7. decision-engine: Playbook Schema (tasks 1.1-1.6)
8. decision-engine: Severity Calculation (tasks 2.1-2.7)
9. decision-engine: Drools Integration (tasks 3.1-3.6)
10. decision-engine: Rule Validation (tasks 4.1-4.3)
11. decision-engine: Conflict Resolution (tasks 5.1-5.3)
12. decision-engine: Alert Versioning (tasks 6.1-6.3)

### R2 — Sprint 1 (Ingestion + Verification)

1. alert-integrator: Module Setup (tasks 1.1-1.7)
2. alert-integrator: Webhook Endpoint (tasks 2.1-2.7)
3. alert-integrator: Adapter Pattern (tasks 3.1-3.5)
4. motor-verificacion: Module Setup (tasks 1.1-1.5)
5. motor-verificacion: Provider Detection (tasks 2.1-2.5)
6. motor-verificacion: Verification (tasks 3.1-3.6)

### R2 — Sprint 2 (Integration + Continuation)

1. alert-integrator: Event/Secret Dedup (tasks 4-5)
2. alert-integrator: Worker Pool + DLQ (tasks 6-7)
3. alert-integrator: Pipeline Integration (tasks 8-9)
4. alert-integrator: Tests (tasks 10-11)
5. motor-verificacion: Permission Enum + Account Mapping (tasks 4-5)
6. motor-verificacion: Cache & CB + Tests (tasks 6-7)
7. logging: Continuation (tasks 3-8)
8. decision-engine: AWS Metadata + Rules Update (tasks 7-8)

### R3 — Sprint 1 (Actions + Polish)

1. action-executor: State Machine (tasks 1.1-1.5)
2. action-executor: AWS STS Flow (tasks 2.1-2.7)
3. action-executor: Notification (tasks 3.1-3.7)
4. spring-boot-arch: Secrets Mgmt (tasks 8.1-8.14)
5. spring-boot-arch: Profiles + Testing (tasks 11-12)
6. action-executor: Audit + Integration + Tests (tasks 4-6)
7. spring-boot-arch: Docker + DB Migrations + Docs (tasks 13-15)

---

## Dependencias entre Releases

```
R1 Foundation (POM, models, SPIs)
    ├──▶ R2 Alert Integrator
    ├──▶ R2 Motor Verificacion
    │       └──▶ R2 Decision Engine (continuation — usa resultado real de verificacion)
    └──▶ R2/R3 Logging (continuation)

R1 Decision Engine (playbooks + Drools)
    └──▶ R3 Action Executor (usa decision output para rotar)

R2 Pipeline (webhook → verification → decision)
    └──▶ R3 Action Executor (usa pipeline completo para accionar)
```

**Bloqueante critico:** R1 Foundation debe estar completa antes de cualquier otro modulo.
**Bloqueante secundario:** R1 Decision Engine debe estar completa antes de R3 Action Executor.

---

## Notas Importantes

1. **Testing R1:** Los tests del decision engine usan `VerificationResult` mockeado. No se requiere webhook ni verificacion real.
2. **Decision Engine es prioridad:** Es el modulo de mayor peso. Se desarrolla primero y recibe mas atencion.
3. **Logging es transversal:** Se implementa en fases. Lo basico va en R1, lo completo en R2.
4. **Spring Boot Arch es transversal:** Foundation en R1, continuation en R2, polish en R3.
5. **Mockeo:** Se usa hasta que el change correspondiente al modulo este implementado.
6. **GUI/Dashboard:** Se menciona en R3 pero no tiene un change de OpenSpec dedicado (se implementa dentro de action-executor).
