## Why

El sistema "Motor de Respuesta a Exposicion de Credenciales" carece de un sistema de observabilidad unificado. Los cuatro cambios activos (`alert-integrator`, `motor-verificacion-credenciales`, `decision-engine-playbook-combination`, `action-executor-credential-rotation`) necesitan logs estructurados, trazabilidad por alerta, redaccion de secretos, y un audit trail para cumplimiento y diagnostico.

Actualmente no existe un mecanismo para:
- Rastrear una alerta desde su ingesta hasta la accion final
- Filtrar logs por tenant, alerta o fase de procesamiento
- Redactar secretos en los logs (requisito de seguridad)
- Auditoria de decisiones tomadas (que regla se activo, por que, que playbook se ejecuto)
- Monitoreo de performance del pipeline (latencia, throughput, tasas de fallo)

## What Changes

- Modulo `logging` autokonfigurable con logstash-logback-encoder para salida JSON estructurada
- MDC filter (`OncePerRequestFilter`) que popula `alert_id`, `client_id`, `trace_id`, `phase`, `step` por request
- Secret redaction custom Logback converter (`SecretRedactionConverter`) que oculta valores de secretos en logs
- Audit trail con tabla `audit_events` en PostgreSQL con campos JSONB para datos estructurados
- Auto-purge de audit events con scheduled task (retencion configurable, default 30 dias)
- Metrics con Micrometer + Spring Boot Actuator (pipeline duration, dedup hit rate, circuit breaker state)
- Log levels diferenciados: INFO, WARN, ERROR, AUDIT

## Capabilities

### New Capabilities
- `observability`: Capacidad central de logging estructurado, trazabilidad contextual, redaccion de secretos, audit trail con persistencia en DB, y metrics de performance

### Modified Capabilities
- Ninguna — este es el primer change en definir esta capa

## Impact

- Nuevo modulo `logging` en el backend Spring Boot con subcomponentes: MDC filter, secret redaction converter, audit event entity/repository, audit service, auto-configuration
- Nueva tabla `audit_events` en PostgreSQL
- Todos los otros changes integran con este modulo automaticamente via auto-configuracion
- Dependencia de `logstash-logback-encoder` para formato JSON
- Dependencia de `micrometer-registry-prometheus` (o el registry que se elija) para metrics
