## Why

El sistema "Motor de Respuesta a Exposicion de Credenciales" necesita un componente de ingesta de alertas que reciba eventos de deteccion en tiempo real desde proveedores externos (GitGuardian como primer proveedor). Actualmente no existe ning mecanismo para recibir alertas de forma instantanea — todos los cambios existentes asumen que el alert model normalizado ya existe y es proveido por un componente que no ha sido implementado.

Los cambios activos (`motor-verificacion-credenciales`, `decision-engine-playbook-combination`, `action-executor-credential-rotation`) dependen de este componente como entrada upstream, pero ninguno define la capa de ingesta.

## What Changes

- Endpoint webhook REST `POST /api/alerts` para ingesta en tiempo real (sin polling)
- Modelo de alerta genérico (`GenericAlertModel`) que abstrae el formato de cualquier proveedor
- Adapter pattern: handler generico con adapters especificos por proveedor (GitGuardianAdapter como primer adapter)
- Validacion de autenticidad de webhooks (signature validation, IP whitelist)
- Sistema de deduplicacion en dos niveles: event-level (TTL 2-5 min) y secret-level (estado-based, TTL configurable)
- Manejo de falsos positivos: cooldown basado en resultado del verifier (FP = cooldown largo, TP = cooldown segun action)
- Queue strategy con worker pool para procesamiento concurrente
- Dead letter queue para alerts con formato invalido o procesamiento fallido

## Capabilities

### New Capabilities
- `alert-ingestion`: Capacidad de ingesta de alertas desde proveedores externos via webhooks en tiempo real, con modelo genérico, adapters por proveedor, deduplicacion en dos niveles, y validacion de autenticidad

### Modified Capabilities
- *Ninguna — este es el primer change en definir esta capa*

## Impact

- Nuevo modulo `alerting` en el backend Spring Boot con subcomponentes: webhook controller, generic parser, adapter registry, dedup cache, dead letter queue, and worker pool
- Dependencia de storage para cache de dedup (Redis o Caffeine para deployment local)
- Input del `alert-ingestion` integra directly con el modulo `verification` del change `motor-verificacion-credenciales` via el `GenericAlertModel`
- Los adapters producen alerts con `detectedSecret.type` explícito, desacoplando al verifier de formatos especificos de GitGuardian
