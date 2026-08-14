## Purpose

Provide centralized, structured observability for the Credential Rotation System including JSON logging, MDC context propagation, secret redaction, audit trail persistence, performance metrics, and auto-configuration.

## Requirements

### Requirement: Structured JSON Logging
El sistema SHALL producir logs en formato JSON estructurado para todos los componentes. El formato de log debe incluir:
- Campos estandar: `timestamp`, `level`, `logger`, `message`
- Campos MDC: `trace_id`, `alert_id`, `client_id`, `phase`, `step`
- Campos opcionales: `duration_ms`, `status`

Los logs deben ser queryables por `client_id`, `alert_id`, `severity`, `phase`, `timestamp`, `duration_ms`.

#### Scenario: Log output is valid JSON with all standard fields
- **WHEN** un componente del sistema escribe un log con nivel INFO
- **THEN** la salida es un objeto JSON valido que incluye `timestamp`, `level`, `logger`, `message` y los campos MDC actuales

#### Scenario: Log output includes MDC contextual fields
- **WHEN** una request esta en progreso con `trace_id`, `alert_id`, `client_id`, `phase`, `step` en el MDC
- **THEN** el log JSON incluye todos estos campos MDC con sus valores actuales

#### Scenario: Logs are queryable by client_id
- **WHEN** un administrador busca logs de un tenant especifico
- **THEN** los logs pueden filtrarse por `client_id` correctamente

### Requirement: Secret Redaction in All Log Output
El sistema SHALL redactar automaticamente todos los valores de secretos en la salida de logs.

#### Scenario: Secret value is redacted in log output
- **WHEN** un log contiene un campo con key `secret`, `password`, `key`, `token`, `access_key`, `api_key`, o `private_key` y un valor de 20+ caracteres
- **THEN** el valor es reemplazado con `[REDACTED]` en la salida del log

#### Scenario: Short values are not redacted
- **WHEN** un log contiene un campo de tipo secret con un valor menor a 20 caracteres
- **THEN** el valor se mantiene sin redaccion (se asume que no es un secreto real)

#### Scenario: Non-secret fields are not affected
- **WHEN** un log contiene campos que no son de tipo secret (ej: `message`, `timestamp`, `phase`)
- **THEN** los campos no secretos se mantienen intactos en la salida

### Requirement: MDC Contextual Fields Population
El sistema SHALL poblar automaticamente campos MDC para cada request entrante.

#### Scenario: trace_id generated when not present in request
- **WHEN** una request webhook entra sin el header `X-Trace-Id`
- **THEN** el sistema genera un UUID y lo establece como `trace_id` en el MDC

#### Scenario: trace_id extracted from request header
- **WHEN** una request webhook incluye el header `X-Trace-Id`
- **THEN** el sistema usa ese valor como `trace_id` en el MDC

#### Scenario: alert_id extracted from webhook payload
- **WHEN** una request webhook incluye un campo de identificador de evento (ej: `sourceEventId`)
- **THEN** el sistema establece `alert_id` en el MDC con ese valor

#### Scenario: alert_id generated when not in payload
- **WHEN** una request webhook no incluye un identificador de evento
- **THEN** el sistema genera un UUID y lo establece como `alert_id` en el MDC

#### Scenario: client_id extracted from authentication context
- **WHEN** una request webhook incluye informacion de tenant/cliente en el header o contexto de autenticacion
- **THEN** el sistema establece `client_id` en el MDC con ese valor

#### Scenario: MDC fields cleared on request completion
- **WHEN** una request webhook finaliza (exitosa o con error)
- **THEN** los campos `trace_id`, `alert_id`, `client_id`, `phase`, `step` son removidos del MDC en un bloque `finally`

### Requirement: Audit Trail Persistence
El sistema SHALL persistir audit events en PostgreSQL tabla `audit_events` para todos los eventos criticos del pipeline.

#### Scenario: Webhook event is logged to audit trail
- **WHEN** un webhook es recibido y procesado exitosamente
- **THEN** el sistema escribe un evento en `audit_events` con `event_type: 'WEBHOOK_RECEIVED'`, `status: 'SUCCESS'`, y los datos del evento en `event_data` JSONB

#### Scenario: Verification started event is logged
- **WHEN** el modulo de verificacion comienza a procesar una alerta
- **THEN** el sistema escribe un evento en `audit_events` con `event_type: 'VERIFICATION_STARTED'` y los datos de la alerta

#### Scenario: Verification completed event is logged
- **WHEN** el modulo de verificacion termina de procesar una alerta
- **THEN** el sistema escribe un evento en `audit_events` con `event_type: 'VERIFICATION_COMPLETED'`, `status: 'SUCCESS'` o `'FAILURE'`, y el resultado en `event_data` JSONB

#### Scenario: Rule evaluation event is logged
- **WHEN** el motor de decision evalua una regla para una alerta
- **THEN** el sistema escribe un evento en `audit_events` con `event_type: 'RULE_EVALUATED'`, la regla evaluada, el razonamiento, y el resultado en `event_data` JSONB

#### Scenario: Action execution event is logged
- **WHEN** el executor de acciones ejecuta una action (ej: credential rotation)
- **THEN** el sistema escribe un evento en `audit_events` con `event_type: 'ACTION_EXECUTED'`, los detalles de la accion, y el resultado en `event_data` JSONB

#### Scenario: DLQ event is logged
- **WHEN** una alerta se mueve a la Dead Letter Queue
- **THEN** el sistema escribe un evento en `audit_events` con `event_type: 'DLQ_ENQUEUED'`, el error descriptivo, y el raw payload en `event_data` JSONB

#### Scenario: Dedup hit is logged
- **WHEN** una alerta es descartada por deduplicacion
- **THEN** el sistema escribe un evento en `audit_events` con `event_type: 'DEDUP_HIT'`, el nivel de dedup (event/secret), y el estado que causo el cooldown en `event_data` JSONB

### Requirement: Audit Event Synchronous Write
El sistema SHALL escribir audit events de forma sincrona (sync) a la base de datos.

#### Scenario: Audit event is persisted before pipeline continues
- **WHEN** un componente llama a `AuditService.logWebhookReceived()`
- **THEN** el evento se persiste en `audit_events` antes de que el metodo retorne

#### Scenario: Audit write failure does not break the pipeline
- **WHEN** el write de un audit event falla (ej: DB connection issue)
- **THEN** el sistema registra el error en los logs pero no lanza excepcion; el pipeline continua normalmente

### Requirement: Audit Retention and Auto-Purge
El sistema SHALL purgar automaticamente los audit events despues de 90 dias de retencion.

#### Scenario: Audit events older than 90 days are purged
- **WHEN** el scheduled purge task se ejecuta (daily at 2:00 AM)
- **THEN** el sistema elimina todos los eventos de `audit_events` con `created_at` anterior a 90 dias

#### Scenario: Purge count is logged
- **WHEN** el scheduled purge task completa la eliminacion
- **THEN** el sistema registra el numero de eventos eliminados en los logs

#### Scenario: Retention period is configurable
- **WHEN** el administrador configura `app.logging.audit-retention-days` en `application.yml`
- **THEN** el purge task usa el valor configurado en lugar del default de 30 dias

### Requirement: Performance Metrics via Micrometer
El sistema SHALL exponer metrics de performance a traves de Micrometer y el endpoint `/actuator/prometheus`.

#### Scenario: Pipeline duration metric is recorded
- **WHEN** una alerta se procesa completamente por el pipeline
- **THEN** el sistema registra un valor en el timer `app.pipeline.duration`

#### Scenario: Dedup hits counter is incremented
- **WHEN** una alerta es descartada por deduplicacion
- **THEN** el sistema incrementa el counter `app.dedup.hits` en 1

#### Scenario: Dedup misses counter is incremented
- **WHEN** una alerta pasa el dedup (no es duplicada)
- **THEN** el sistema incrementa el counter `app.dedup.misses` en 1

#### Scenario: Webhook received counter is incremented
- **WHEN** un webhook es recibido por el endpoint `/api/v1/alerts`
- **THEN** el sistema incrementa el counter `app.webhook.received` en 1

#### Scenario: Webhook failed counter is incremented
- **WHEN** un webhook falla la validacion (signature o IP)
- **THEN** el sistema incrementa el counter `app.webhook.failed` en 1

#### Scenario: Circuit breaker state is exposed as gauge
- **WHEN** un circuit breaker cambia de estado (CLOSED → OPEN, OPEN → HALF_OPEN, etc.)
- **THEN** el sistema actualiza el gauge `app.circuit.breaker.state` con el estado actual

#### Scenario: Prometheus endpoint is accessible
- **WHEN** un sistema de monitoreo hace un GET a `/actuator/prometheus`
- **THEN** el sistema responde con las metrics en formato Prometheus (text/plain)

### Requirement: Auto-Configuration
El sistema SHALL auto-configurarse sin requerir cambios en los modulos de otros cambios.

#### Scenario: Logging components are auto-configured on startup
- **WHEN** la aplicacion Spring Boot inicia
- **THEN** el MDC filter, audit service, y purge service se registran automaticamente como beans de Spring

#### Scenario: Secret redaction converter is registered automatically
- **WHEN** el sistema inicia con `logback-spring.xml`
- **THEN** el `SecretRedactionConverter` se registra en el encoder de Logback automaticamente

#### Scenario: Audit events table is created automatically
- **WHEN** la aplicacion inicia con Flyway o Liquibase
- **THEN** la tabla `audit_events` se crea si no existe, con todos los indices necesarios

### Requirement: Log Levels
El sistema SHALL soportar cuatro niveles de log: INFO, WARN, ERROR, AUDIT.

#### Scenario: INFO level for normal pipeline flow
- **WHEN** una alerta se procesa normalmente (arrival, adapter selection, verification start/end, rule evaluation start)
- **THEN** el sistema escribe logs con nivel INFO

#### Scenario: WARN level for dedup hits and circuit breaker trips
- **WHEN** una alerta es descartada por dedup o un circuit breaker se trip
- **THEN** el sistema escribe logs con nivel WARN

#### Scenario: ERROR level for validation and processing failures
- **WHEN** un webhook falla la validacion de signature, un adapter falla, o el pipeline lanza una excepcion
- **THEN** el sistema escribe logs con nivel ERROR

#### Scenario: AUDIT level for state transitions and security events
- **WHEN** una action se ejecuta, una regla se evalua, una alerta se mueve a DLQ, o un resultado de verificacion se produce
- **THEN** el sistema escribe logs con nivel AUDIT

#### Scenario: AUDIT logs are written to both output and audit_events table
- **WHEN** un componente escribe un log con nivel AUDIT
- **THEN** el sistema escribe el log en la salida estandar Y persiste el evento en `audit_events`
