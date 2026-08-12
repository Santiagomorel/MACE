# Inconsistencias entre Cambios OpenSpec

> Generado: 2026-08-11
> Resuelto: 2026-08-12
> Cambios analizados: 6 (todos `in-progress`)
> Estrategia: Opcion A — `spring-boot-architecture` como unification/orchestration, no duplication

## Resoluciones

### #1 [CRITICA] Retention de Audit Events: 90 dias (UNIFICADO)

**Source of Truth:** `logging-infrastructure/design.md` (Decision 5)

**Cambios aplicados:**
- `logging-infrastructure/specs/logging/spec.md` → line 108: "30 dias" → "90 dias"
- `spring-boot-architecture/specs/observability/spec.md` → lines 77, 81, 86: "30 days" → "90 days"
- `spring-boot-architecture/tasks.md` 9.11: "30 days" → "90 days"

**Valor final:** **90 dias** — cubre el ciclo tipico de auditoria empresarial (3 meses)

---

### #2 [CRITICA] Modulo `logging` — logging-infrastructure es el owner

**Source of Truth:** `logging-infrastructure` change

**Cambios aplicados:**
- `spring-boot-architecture/tasks.md` Section 9 → todas las tareas 9.1-9.3, 9.9-9.14 referencian `logging-infrastructure`
- `spring-boot-architecture/tasks.md` 9.4-9.8 → integran (actuator, metrics, health) pero no re-declaran el logging
- `spring-boot-architecture/design.md` → converger a referencia en futuras ediciones

**Arquitectura final:**
```
logging-infrastructure change (owner del modulo logging)
  ├── logging, logging.filter, logging.converter
  ├── logging.model, logging.repository, logging.service
  └── tasks.md (secciones 1-6)

spring-boot-architecture change (integra el modulo)
  └── tasks.md Section 9 → "ver logging-infrastructure para detalles"
```

---

### #3 [CRITICA] Dedup Cache: Caffeine (UNIFICADO)

**Source of Truth:** `alert-integrator/design.md` (Decision 5)

**Cambios aplicados:**
- `alert-integrator/proposal.md` → "Redis o Caffeine para deployment local" → "Redis para cache distribuido, Caffeine para deployment local"
- `spring-boot-architecture/design.md` Decision 3 → ya dice "Phase 1 usa Caffeine (local)" ✅ correcto
- `spring-boot-architecture/proposal.md` → agregada nota sobre Caffeine con Redis migration path

**Decision final:** Caffeine para Phase 1. Redis como migration path documentado para multi-instancia (Phase 3+)

---

### #4 [MODERADA] MDC Context: OncePerRequestFilter (UNIFICADO)

**Source of Truth:** `logging-infrastructure/tasks.md` (2.1)

**Cambios aplicados:**
- `spring-boot-architecture/tasks.md` 9.2: `HandlerInterceptor` → `OncePerRequestFilter` + referencia a logging-infrastructure

**Rationale:** Filter corre antes en el chain → mas contexto disponible para webhooks

---

### #5 [MODERADA] API Endpoint Paths: /api/v1/ (UNIFICADO)

**Source of Truth:** `spring-boot-architecture/proposal.md` (define el estandar del proyecto)

**Cambios aplicados:**
- `alert-integrator/design.md` → `/api/alerts` → `/api/v1/alerts`
- `alert-integrator/proposal.md` → `/api/alerts` → `/api/v1/alerts`
- `alert-integrator/tasks.md` → `/api/alerts` → `/api/v1/alerts` (tasks 1.3, 2.1)
- `alert-integrator/specs/alert-ingestion/spec.md` → todos los `/api/alerts` → `/api/v1/alerts`
- `logging-infrastructure/specs/logging/spec.md` → `/api/alerts` → `/api/v1/alerts`

**Paths finales:** `POST /api/v1/alerts`, `GET /api/v1/verification/{alertId}`, etc.

---

### #6 [MODERADA] GenericAlertModel — spring-boot-arch es el owner

**Source of Truth:** `spring-boot-architecture/tasks.md` 2.10 (shared/models)

**Decision:** `GenericAlertModel` se define UNA sola vez en `shared/models` de spring-boot-arch.
Todos los otros cambios lo consumen via SPI (`AlertAdapter` interface).

**No se requiere cambio de archivos** — los otros cambios ya referencian el modelo (no lo re-definen como clase).

---

### #7 [MODERADA] NotificationDispatcher — action-executor es el owner

**Source of Truth:** `action-executor-credential-rotation/design.md` (Decision 3) + tasks Section 3

**Cambios aplicados:**
- `spring-boot-architecture/design.md` Decision 5 → reemplazado con referencia a action-executor
- `spring-boot-architecture/tasks.md` Section 7 → tasks 7.7 ahora referencia action-executor

**Arquitectura final:**
```
action-executor (owner del NotificationDispatcher)
  └── NotificationChannel SPI (shared/spi) ← definido por action-executor, consumido por otros

spring-boot-arch → referencia action-executor para detalles de implementacion
```

---

### #8 [MODERADA] Credenciales Admin — spring-boot-arch es mas explicito

**Source of Truth:** `spring-boot-architecture/tasks.md` Section 8 (AWS SM + PostgreSQL AES-256)

**Decision:** `motor-verificacion` y `action-executor` ya usan "vault" como terminologia.
Como spring-boot-arch define la estrategia concreta (AWS SM + PostgreSQL fallback), los otros
cambios deberian referenciarlo en futuras ediciones.

**No se requiere cambio inmediato** — "vault" es una abstraccion valida en los otros cambios
mientras converjan a la estrategia de spring-boot-arch.

---

### #9 [MENOR] Spec MIGRADO → movido a notes/

**Cambios aplicados:**
- `decision-engine-playbook-combination/specs/action-executor/spec.md` → movido a
  `decision-engine-playbook-combination/notes/migrated-action-executor-spec.md`
- Mantiene referencia historica sin romper validacion de OpenSpec

---

### #10 [MENOR] Naming `verification-engine` (UNIFICADO)

**Source of Truth:** `spring-boot-architecture` define el Maven module como `verification-engine`

**Decision:** `verification-engine` es el nombre correcto. Referencias en otros cambios que usan
"verification" como concepto/componente (no como nombre de modulo) son aceptables.

**No se requiere cambio de archivos** — las referencias existentes son conceptuales, no modulares.

---

### #11 [MENOR] Numbering spring-boot-arch tasks.md

**Cambios aplicados:**
- Al eliminar tasks duplicados de Section 8 y condensing Section 7, el numbering es ahora:
  - Section 7 termina en 7.7 (sin saltos)
  - Section 8 comienza en 8.1 (sin 8.0 erroneo)
  - Sections 11-15 mantienen numbering consistente de dos digitos

---

### #12 [MENOR] Section 14 incompleta — NO APLICA

**Nota:** La section 14 de `spring-boot-architecture/tasks.md` (Database Migrations) ya existe
completa con 6 tareas (V1__init_schema.sql, V2__seed_default_rules.sql, etc.).
El doc original de inconsistencias estaba desactualizado.

---

### #13 [INFORMATIVO] Spring-boot-arch como umbrella — Opcion A adoptada

**Decision:** `spring-boot-architecture` es unification + orchestration, no consolidation.

```
spring-boot-architecture change
├── Define: parent POM, module DAG, SPI contracts, deployment, secrets strategy
├── Referencia (no redefine):
│   ├── alert-integrator → dedup, webhook ingestion, adapter pattern
│   ├── logging-infrastructure → logging, MDC, audit trail, retention
│   ├── action-executor → notification dispatcher, rotation services
│   ├── decision-engine → Drools rules, playbook manager
│   └── motor-verificacion → credential verification
└── Integra: ensambla todos los modulos juntos, enforcement de limites
```

**Cambios aplicados:**
- `spring-boot-architecture/tasks.md` → remove ~40 tareas duplicadas (logging, notifications)
- `spring-boot-architecture/design.md` Decision 5 → referencia action-executor
- `spring-boot-architecture/tasks.md` Section 9 → referencia logging-infrastructure
- `spring-boot-architecture/tasks.md` Section 7 → referencia action-executor

---

# Estado Final

| # | Inconsistencia | Resolucion | Impacto |
|---|---------------|------------|---------|
| 1 | Retention 30 vs 90 dias | **90 dias** — sincronizado en todos los archivos | ✅ Resuelto |
| 2 | Modulo logging standalone vs distribuido | **logging-infrastructure owner** — spring-boot-arch referencia | ✅ Resuelto |
| 3 | Caffeine vs Redis dedup | **Caffeine Phase 1** — Redis como migration path | ✅ Resuelto |
| 4 | Filter vs Interceptor MDC | **OncePerRequestFilter** — logging-infrastructure | ✅ Resuelto |
| 5 | API paths /api/ vs /api/v1/ | **/api/v1/** — alert-integrator convergió | ✅ Resuelto |
| 6 | GenericAlertModel doble definicion | **spring-boot-arch owner** (shared/models) | ✅ Resuelto (sin cambios) |
| 7 | NotificationDispatcher interfaz | **action-executor owner** — spring-boot-arch referencia | ✅ Resuelto |
| 8 | Terminologia credentials | **spring-boot-arch explicito** — "vault" es abstraccion valida | ✅ Resuelto (sin cambios) |
| 9 | Spec MIGRADO sin deltas | **Movido a notes/** | ✅ Resuelto |
| 10 | naming verification-engine | **verification-engine** — referencia es conceptual | ✅ Resuelto (sin cambios) |
| 11 | Numbering task 7.9 omitido | **Corregido** al remover tareas duplicadas | ✅ Resuelto |
| 12 | Section 14 truncada | **NO APLICA** — seccion ya existe completa | ✅ Resuelto |
| 13 | Umbrella vs consolidation | **Opcion A: unification** — spring-boot-arch no duplica | ✅ Resuelto |
