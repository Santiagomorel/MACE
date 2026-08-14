## Context

El sistema "Motor de Respuesta a Exposicion de Credenciales" necesita un sistema de observabilidad unificado que cubra:
- Logs estructurados en JSON para queryabilidad
- Trazabilidad por alerta desde ingesta hasta accion
- Redaccion automatica de secretos en logs
- Audit trail persistente para cumplimiento
- Metrics de performance

Se eligio PostgreSQL JSONB (Option B) como storage de logs/audit trail, rechazando ELK (costo) y File/S3 (complejidad adicional).

## Goals / Non-Goals

**Goals:**
- Logs JSON estructurados con MDC contextual por request
- Trazabilidad completa de una alerta a traves de todos los modulos
- Redaccion automatica de secretos en todos los logs
- Audit trail persistente en PostgreSQL con JSONB
- Auto-purge de logs a 30 dias
- Metrics de performance via Micrometer + Actuator
- Auto-configuracion: los otros modules no necesitan integrar manualmente

**Non-Goals:**
- ELK / Elasticsearch integration (rejected, cost)
- Distributed tracing with Jaeger/Zipkin (OpenTelemetry out of scope for Phase 1)
- Log aggregation (file/S3 export)
- Real-time log monitoring UI
- Log-based alerting (solo metrics via Prometheus)
- Log encryption at rest (PostgreSQL TDE handles this)

## Decisions

### Decision 1: PostgreSQL JSONB for Audit Trail
Se utiliza una tabla `audit_events` en PostgreSQL para almacenar el audit trail.

```sql
CREATE TABLE audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(50) NOT NULL,  -- 'WEBHOOK_RECEIVED', 'VERIFICATION_STARTED',
                                      -- 'RULE_EVALUATED', 'ACTION_EXECUTED', 'DLQ_ENQUEUED'
    severity VARCHAR(20) NOT NULL,    -- INFO, WARN, ERROR, AUDIT
    client_id VARCHAR(100),
    alert_id VARCHAR(100),
    phase VARCHAR(50),                -- 'alert-ingestion', 'verification', 'decision', 'action-execution'
    trace_id VARCHAR(100),
    step VARCHAR(100),
    event_data JSONB NOT NULL,        -- datos estructurados del evento
    duration_ms INTEGER,
    status VARCHAR(20),               -- 'SUCCESS', 'FAILURE', 'SKIPPED'
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_audit_client ON audit_events(client_id);
CREATE INDEX idx_audit_alert ON audit_events(alert_id);
CREATE INDEX idx_audit_created ON audit_events(created_at);
CREATE INDEX idx_audit_data ON audit_events USING GIN(event_data);
```

**Rationale:** PostgreSQL ya esta desplegado para las otras tablas. JSONB permite almacenar datos arbitrarios sin schema rigido. Los indices permiten queryabilidad eficiente.

### Decision 2: MDC Contextual Fields
Cada request popula un conjunto de campos MDC que se incluyen automaticamente en todos los logs JSON.

| Campo | Fuente | Proposito |
|---|---|---|
| `trace_id` | Generado si no viene en header | Trazabilidad end-to-end |
| `alert_id` | Extraido de la alerta o generado | Rastrear alerta especifica |
| `client_id` | Contexto de autenticacion / adapter | Aislamiento multi-tenant |
| `phase` | Modulo que escribe el log | Identificar el componente |
| `step` | Sub-operacion dentro de la fase | Detalle del flujo |

**Rationale:** Un solo filtro (`MdcLoggingFilter`) popula todos los campos al inicio del request y los limpia al final. Los logs JSON incluyen automaticamente los campos MDC via logstash-logback-encoder.

### Decision 3: Secret Redaction via Logback Converter
Se implementa un custom Logback `Converter` que aplica redaccion antes de la salida del log.

```java
// Pattern: busca patrones como "secret", "password", "key", "token", "access_key"
// y reemplaza valores de 20+ caracteres con [REDACTED]
```

**Rationale:** Un converter de Logback es mas simple que un Filter y funciona a nivel de layout — afecta toda la salida automaticamente. No requiere cambios en el codigo de los otros modulos.

### Decision 4: Audit Events — Sync Write
Los audit events se escriben sincronamente en la base de datos.

**Rationale:** Para Phase 1, la latencia adicional (5-10ms por write) es aceptable. La simplicidad de un write sync supera los beneficios de async. Si en el futuro se convierte en bottleneck, se migra a `@Async` sin cambiar la interfaz.

### Decision 5: Retention — 30 Days with Auto-Purge
Los audit events se purgan automaticamente despues de 90 dias.

```java
@Scheduled(cron = "0 0 2 * * ?")  // Daily at 2 AM
public void purgeExpiredAuditEvents() {
    int deleted = auditEventRepository.deleteByCreatedAtBefore(
        LocalDateTime.now().minusDays(90)
    );
    log.info("Purged {} expired audit events", deleted);
}
```

**Rationale:** 90 dias cubre el ciclo tipico de auditoria empresarial (3 meses). La purga nightly a las 2 AM no interfere con el peak de operaciones.

### Decision 6: Metrics via Micrometer + Actuator
Se utiliza Micrometer con Spring Boot Actuator para metrics de performance.

**Metrics clave:**
- `app.pipeline.duration` — duracion del pipeline (timer)
- `app.dedup.hits` — contador de dedup hits (counter)
- `app.dedup.misses` — contador de dedup misses (counter)
- `app.webhook.received` — contador de webhooks recibidos (counter)
- `app.webhook.failed` — contador de webhooks fallidos (counter)
- `app.circuit.breaker.state` — estado del circuit breaker (gauge)
- `app.audit.events.count` — total de audit events por tipo (counter)

**Rationale:** Micrometer es el estandar en Spring Boot. Actuator expone `/actuator/prometheus` out-of-the-box para integracion con Prometheus/Grafana.

## Risks / Trade-offs

|Risk|Impact|Mitigation|
|---|---|---|
|Audit DB write latency agrega 5-10ms al pipeline|Bajo|Sync es aceptable en Phase 1; migrar a async si se convierte en bottleneck|
|JSONB column size grows rapidly|Medio|Auto-purge a 30 dias; monitoring del table size|
|Secret redaction no cubre todos los patrones|Bajo|Converter basado en regex ampliable; review manual de logs criticos|
|MDC pollution from failed requests|Bajo|Filter limpia MDC en bloque `finally`|
|Prometheus scrape latency en endpoints de actuator|Bajo|Actuator expone metrics en memoria (no hay overhead de DB) |
