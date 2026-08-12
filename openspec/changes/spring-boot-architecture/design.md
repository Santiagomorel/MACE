## Context

El sistema "Motor de Respuesta a Exposicion de Credenciales" tiene 5 cambios activos (`alert-integrator`, `decision-engine-playbook-combination`, `logging-infrastructure`, `motor-verificacion-credenciales`, `action-executor-credential-rotation`) que contienen decisiones tecnicas dispersas en 5 documentos de diseño independientes. Cada cambio define su propio modulo, pero no existe un diseño unificado que explique como se organizan todos los modulos juntos: estructura del proyecto, limites de dependencias, comunicacion inter-modulo, estrategia de despliegue y gestion de credenciales admin del cliente (Punto 7).

La arquitectura actual debe responder:
- Como se organizan los 6 modulos en un DAG lineal con enforcement de dependencias
- Como las interfaces en `shared/spi/` desacoplan la comunicacion inter-modulo
- Como el versioning SemVer en SPIs previene breaking changes en produccion
- Como el strategy pattern se usa en NotificationDispatcher (Slack, Email, Ticket, SNS)
- Como la comunicacion reactiva via worker pool maneja picos de alerts
- Como el sistema escala de POC ($0) a AWS Free Tier a ECS/RDS
- Como las credenciales admin del cliente se protegen en doble capa (AWS Secrets Manager + AES-256) con rotation cada 90 dias

## Goals / Non-Goals

**Goals:**
- Consolidar todas las decisiones tecnicas de los 5 cambios en un unico documento de diseno
- Definir la estructura Maven multi-module con 6 modulos en DAG lineal
- Establecer limites de dependencias enforcement via compilacion Maven
- Definir la capa de contratos (SPIs) con versioning SemVer
- Especificar la estrategia de deployment por fases (POC $0 → AWS Free Tier → ECS/RDS)
- Documentar la gestion de credenciales admin del cliente (doble capa, rotation, POC vs prod)
- Consolidar observability, testing, secrets management y deployment en decisiones coherentes

**Non-Goals:**
- Implementacion de codigo — solo diseño y decisiones
- Especificacion de reglas Drools detalladas (ver `decision-engine` spec)
- Detalle de payloads de webhooks de proveedores (ver `alert-ingestion` spec)
- Infraestructura as Code detallada (Terraform para Phase 3, ver `deployment` spec)

## Decisions

### Decision 1: Maven Multi-Module con Parent POM

Se selecciona Maven multi-module con un parent POM que centraliza version management y dependency declarations.

**Rationale:** Maven es el estandar en el ecosistema Spring Boot. El parent POM permite:
- Centralizar versiones (Spring Boot 3.3.x, Java 21, AWS SDK v2, Drools/KIE)
- Aplicar plugins globales (JaCoCo, compiler, exec)
- Definir `dependencyManagement` con BOM para version consistency

```
parent-pom/                    ← version management + dependencyManagement
├── shared/
│   ├── shared-models/         ← com.company.rotations.models.*
│   └── shared-spi/            ← com.company.rotations.spi.*
├── alert-integrator/          ← com.company.rotations.alerting.*
├── verification-engine/       ← com.company.rotations.verification.*
├── decision-engine/           ← com.company.rotations.decision.*
└── action-executor/           ← com.company.rotations.actions.*
```

**Dependency DAG:**
```
shared/models → shared/spi → alert-integrator → verification-engine → decision-engine → action-executor
```

**Alternativas consideradas:**
- Gradle: Mejor performance en builds grandes, pero Maven tiene mejor soporte para Spring Boot BOM y es el estandar del equipo
- Single module: Mas simple, pero no escala para 6 modulos con limites de dependencias claros

### Decision 2: Contract Layer via SPI Interfaces con SemVer

Se usan interfaces en `shared/spi/` como unico mecanismo de comunicacion inter-modulo. Modulos importan interfaces, nunca clases concretas de otros modulos.

**Rationale:**
- Desacoplamiento total: cada modulo es independentemente deployable y testeable
- Versioning SemVer en POM: breaking changes avanzan a major version (1.x.x → 2.0.0), backward-compatible (default methods) avanzan a minor (1.0.0 → 1.1.0)
- Enforcement de limites: si un modulo importa un paquete que no esta en su POM, la compilacion Maven falla

**SPIs existentes:**
| SPI | Modulo Proveedor | Modulo Consumidor |
|-----|------------------|-------------------|
| `AlertAdapter` | `alert-integrator` | `verification-engine` |
| `VerificationProvider` | `verification-engine` | `decision-engine` |
| `PlaybookManager` | `decision-engine` | `action-executor` |
| `RotationService` | `action-executor` | (externo) |
| `NotificationChannel` | `action-executor` | (externo) |

**Alternativas consideradas:**
- Event bus (Kafka/RabbitMQ): Overhead innecesario para comunicacion intra-process en Phase 1
- REST inter-modulo: Latencia y overhead de serializacion para comunicacion en el mismo JVM

### Decision 3: Worker Pool Reactive con Backpressure

El alert-ingestion usa un worker pool de tamano fijo (default 5) con cola acotada (default 1000) para procesamiento asincrono.

**Rationale:**
- Un pool de tamano fijo con cola de respaldo maneja picos sin saturar
- Cada worker ejecuta: normalize → verify → decision → (output a action-executor)
- Si la cola esta llena → backpressure: el endpoint devuelve 429 Too Many Requests
- Alerts que fallan van a la Dead Letter Queue (DLQ) en PostgreSQL `alert_dlq`

```
Webhook → [Event Dedup] → [Secret Dedup] → [Worker Queue] → Workers → Verifier
     │              │               │                │
     │         Caffeine         Caffeine+DB      bounded (1000)
     │         5min TTL       state-based      → 429 si llena
     ▼              ▼               ▼
   SKIP          SKIP/COOLDOWN   PROCESS
```

**Deduplicacion en 2 niveles:**
1. **Event-level (TTL 5min):** Key = `sourceEventId`, Caffeine in-memory cache
2. **Secret-level (variable):** Key = `valueHash + repository`, consulta estado del verifier:
   - `false_positive` → cooldown 24h
   - `true_positive` + action completada → cooldown 1h
   - `in_progress` → skip inmediato

**Alternativas consideradas:**
- Redis para dedup: Necesario solo cuando se desplieguen multiples instancias. Phase 1 usa Caffeine (local).
- Thread-per-request: Saturaria el sistema bajo picos de alerts.

### Decision 4: Drools/KIE Rules Engine con KieContainer por Tenant

Se usa Drools como motor de reglas para evaluar resultados de verificacion y determinar si se requiere rotacion.

**Rationale:**
- Reglas en `.drl` (Drools Rule Language) son legibles y mantenibles
- Hot-reload: cada tenant tiene su propio `KieContainer` que se reconstruye sin afectar a otros
- Fallback a `KieContainer` default cuando un tenant no tiene reglas custom
- Integracion nativa con Spring Boot via `kie-spring`

**Severity Floor:**
La severidad final es el maximo entre el resultado de Drools y el playbook floor definido en YAML.
```
finalSeverity = max(droolsResult.severity, playbook.severityFloor)
```

**Playbook Manager:**
Carga playbooks YAML que definen procedimientos de rotacion por tipo de credential:
```yaml
- name: aws-access-key-rotation
  credentialType: AWS_ACCESS_KEY
  severityFloor: high
  steps:
    - invalidate_old_key
    - create_new_key
    - verify_new_key
    - update_configurations
    - notify
```

**Alternativas consideradas:**
- Reglas en BD: Mas flexible, pero requiere recompilacion completa del KieContainer al cambiar reglas
- Reglas hardcodeadas: Menos flexible, pero sin overhead de serializacion y parsing de `.drl`

### Decision 5: NotificationDispatcher — See `action-executor-credential-rotation`

El NotificationDispatcher se implementa como parte del change `action-executor-credential-rotation` (ver sus tasks Section 3).

**Rationale:**
- El strategy pattern y los canales de notificacion (Slack, Email, Ticket, SNS) estan definidos en action-executor
- Spring-boot-arch referencia esta decision via SPI (`NotificationChannel` en `shared/spi`)
- Para detalles de implementacion, ver `action-executor-credential-rotation/design.md` Decision 3 y tasks Section 3

### Decision 6: Hybrid Secrets Management (AWS SM + PostgreSQL AES-256)

Se usa una estrategia hibrida: AWS Secrets Manager (primario para prod/staging) + PostgreSQL encrypted columns (fallback/backup para dev/test).

**Rationale:**
- **Production/Staging:** AWS Secrets Manager con path `/app/rotation/{secret-path}` o `/clients/{tenantId}/{secret-type}`
- **POC/Dev:** PostgreSQL con AES-256 column encryption via JPA `AttributeConverter`
- **Double-layer para tenant credentials:** AWS SM (primario) + encrypted backup en DB (secundario)

**Flujo de retrieval:**
```
1. Intentar AWS Secrets Manager (primario)
   ├─ Exitoso → retornar credentials
   └─ Fallido → 2. Fallback a PostgreSQL (backup)
       ├─ Exitoso → retornar credentials (log de fallback)
       └─ Fallido → 3. Retornar error
```

**AES-256 Column Encryption:**
- JPA `AttributeConverter<String, String>` que cifra/decifra en persistencia
- Key cargada desde env var `ENCRYPTION_MASTER_KEY` en POC
- Enviros cifrados nunca expuestos en logs (toString mascara con `****`)

**Secret Redaction:**
- Logback custom converter que detecta patrones (`AKIA[A-Z0-9]{16}` para AWS keys)
- Reemplaza con `***REDACTED***` a nivel de appender (todas las salidas)

**Alternativas consideradas:**
- Solo AWS Secrets Manager: No funciona en POC sin AWS ($0 constraint)
- Solo PostgreSQL encrypted: Menos seguro que AWS SM para produccion, pero funcional para POC

### Decision 7: Credential Rotation con Dual-Write

Se implementa un estado maquina para rotation con estrategia dual-write para zero downtime.

**Rationale:**
- **State Machine:** `PENDING → ROTATING → SUCCESS | FAIL` con retry up to 3 attempts
- **Dual-Write:** Nuevas credentials escritas a AWS SM + DB encrypted backup ANTES de hacer switch
- **Verification:** Nuevas credentials verificadas antes de limpiar las viejas
- **Timeout:** 5 minutos por intento de rotation

**Proceso de rotation:**
```
1. Generar nuevas credentials (AWS API)
2. Dual-write: AWS SM + DB encrypted backup
3. Verify nuevas credentials (AWS STS GetCallerIdentity)
   ├─ Exitoso → actualizar rotated_at, archivar viejas → SUCCESS
   └─ Fallido → rollback (eliminar nuevas de SM) → FAIL
```

**Rotation failure escalation:**
Despues de 3 intentos → estado `ESCALATE` → notificar por todos los canales.

**Alternativas consideradas:**
- Atomic swap: Riesgoso si la verificacion falla; mejor tener dual-write + rollback explícito
- Sin verificacion: Peligroso — las nuevas credentials podrian estar rotas sin que el sistema lo sepa

### Decision 8: Deployment por Fases ($0 → AWS Free Tier → ECS/RDS)

El sistema sigue una estrategia de despliegue escalonado que minimiza costos en Phase 1.

**Phase 1 — POC ($0):**
- Docker Compose local (Spring Boot + PostgreSQL)
- GitHub Actions solo para CI (no deployment automatizado)
- Credentials en `.env.{tenant}` (no en git, `.gitignore`)
- Sin AWS servicios requeridos

**Phase 2 — AWS Free Tier (12 meses, $0-10/mes):**
- EC2 t3.micro (o AWS Lightsail) para la aplicacion
- RDS PostgreSQL db.t3.micro para la base de datos
- AWS Secrets Manager para almacenamiento de secretos
- GitHub Actions para CI/CD con deployment automatico

**Phase 3 — Produccion ($57-97/mes):**
- Amazon ECS (Fargate) para container orchestration
- RDS PostgreSQL single-AZ para la base de datos
- AWS Secrets Manager con rotation automatica
- Blue-green deployment para zero-downtime releases
- Terraform para Infrastructure as Code

**Docker Multi-Stage Build:**
```
Stage 1 (Build): maven:3.9-jdk-21 → mvn clean package
Stage 2 (Runtime): eclipse-temurin:21-jre-alpine → java -jar app.jar
```

**Alternativas consideradas:**
- Kubernetes desde el inicio: Overhead excesivo para POC y primeros usuarios
- Serverless (Lambda + API Gateway): No compatible con Drools/KIE (JVM limitation)

### Decision 9: Observability — Structured Logging + Actuator + Audit Trail

Se implementa un stack de observabilidad con 4 componentes: Logback JSON, Spring Actuator, Micrometer metrics, y audit trail via `audit_events` table.

**Rationale:**
- **Logback JSON:** Estandar para log aggregation (Datadog, CloudWatch, ELK)
- **MDC:** Context propagation (tenantId, alertId, sessionId) en todos los log lines
- **Actuator:** Health, readiness, liveness probes para container orchestration
- **Micrometer:** Prometheus scrape format para monitoring (Grafana, CloudWatch)
- **Audit Events:** Tabla PostgreSQL con 30-day retention y auto-purge

**Audit Event Types:**
`ALERT_INGESTED`, `ALERT_DEDUPLICATED`, `CREDENTIAL_VERIFIED`, `CREDENTIAL_EXPIRED`, `ROTATION_STARTED`, `ROTATION_COMPLETED`, `ROTATION_FAILED`, `ESCALATION_TRIGGERED`, `CREDENTIAL_ACCESSED`

**Metricas principales:**
- HTTP: request duration, status code, method
- Domain: `alerts.ingested`, `alerts.deduplicated`, `verification.completed`, `rotation.completed`, `rotation.failed`

**Alternativas consideradas:**
- ELK stack propio: Overhead de infraestructura; usar CloudWatch/Logstash en su lugar
- Custom metrics DB: Spring Boot Actuator + Prometheus es suficiente para Phase 1-2

## Risks / Trade-offs

| Risk | Impact | Mitigation |
|------|--------|------------|
| DEDUP local (Caffeine) no comparte entre instancias | Medio | Migrar a Redis cuando se necesiten multiples instancias en Phase 3 |
| Worker pool saturado bajo picos de alerts | Medio | Queue bounded (1000) con backpressure → 429; DLQ para alerts que exceden timeout |
| KieContainer hot-reload no valida reglas nuevas | Alto | Validacion de sintaxis DRL en CI; KieContainer nuevo solo si compilacion exitosa |
| AES-256 key en env var `ENCRYPTION_MASTER_KEY` | Alto | Solo en POC; en prod se migra a AWS SM o AWS KMS para encryption key management |
| AWS Secrets Manager unavailable → fallback a DB | Bajo | Fallback es solo temporal; alertas en logs; recovery automatico cuando SM vuelve |
| Rotation timeout de 5min insuficiente para RDS | Bajo | Timeout configurable por credential type; RDS puede necesitar mas tiempo |
| Multi-module Maven build lento | Bajo | `mvn -pl` para builds de modulo individual; `mvn -am` para build con dependencies |
| Drools reglas `.drl` no versionadas | Medio | Reglas en git con el codigo; admin API valida schema antes de hot-reload |

## Migration Plan

### Phase 0: Scaffold (Primera iteracion)
1. Crear parent POM con 6 modulos en DAG lineal
2. Crear `shared/models` con domain entities (Alert, VerificationResult, DecisionOutput, RotationAction, AuditEvent)
3. Crear `shared/spi` con interfaces (AlertAdapter, VerificationProvider, PlaybookManager, NotificationChannel, RotationService)
4. Crear estructura de paquetes en cada modulo
5. Configurar plugins globales (JaCoCo, compiler, exec-maven)

### Phase 1: Core Modules (Siguientes iteraciones)
1. Implementar `alert-integrator`: webhook endpoint, GitGuardianAdapter, dedup, worker pool, DLQ
2. Implementar `verification-engine`: AWS STS provider, blast radius calculator, severity rules
3. Implementar `decision-engine`: Drools rules, KieContainer manager, playbook YAML loader
4. Implementar `action-executor`: state machine, AWS rotation services, notification dispatcher

### Phase 2: Cross-Cutting
1. Implementar `@ControllerAdvice` para error handling global
2. Configurar Logback JSON + MDC + secret redaction
3. Configurar Spring Actuator (health, readiness, liveness, metrics, prometheus)
4. Implementar `audit_events` table con auto-purge
5. Implementar AES-256 column encryption + SecretVaultService

### Phase 3: Testing
1. Unit tests con JUnit 5 + Mockito + AssertJ (target 70% general, 80%+ domain)
2. Integration tests con Testcontainers PostgreSQL
3. Web tests con MockMvc (HMAC validation, validation errors)
4. Drools rule tests con KieFileSystem
5. Coverage gates con JaCoCo en CI

### Phase 4: Deployment
1. Crear Docker multi-stage builds
2. Crear `docker-compose.yml` para local/staging
3. Configurar GitHub Actions CI/CD
4. Configurar profiles (dev/staging/prod)
5. Deploy en Phase 1 (POC) → Phase 2 (AWS Free Tier) → Phase 3 (ECS/RDS)

## Open Questions

1. **GitGuardian webhook signature format exacto:** Confirmar el header y algoritmo exacto que GitGuardian usa para firmar los webhooks (documentacion de su API v2)

2. **TTL de cooldown para true_positive:** 1 hora es suficiente? Podria ser que el usuario rote una key y GitGuardian la re-detecte en el history en < 1h?

3. **Notificacion de DLQ:** Como se notifica al equipo cuando hay alerts en la DLQ? Email? Slack? Log alert?

4. **Drools rule validation:** Como se validan las reglas `.drl` nuevas antes del hot-reload? Validacion de sintaxis solo, o ejecucion de smoke tests?

5. **AWS credentials scope para rotation:** Que IAM permissions minimas necesita el rotation service? `iam:CreateAccessKey`, `iam:DeactivateMFADevice`, `sts:GetCallerIdentity`?

6. **Encryption key rotation para AES-256:** Como se rota la `ENCRYPTION_MASTER_KEY` sin perder datos existentes en PostgreSQL? Necesario en Phase 1?

7. **Tenant onboarding:** Como se provisionan los tenant credentials inicialmente? Manual via admin API? Automated via setup wizard?
