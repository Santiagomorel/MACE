## Why

El proyecto "Motor de Respuesta a Exposicion de Credenciales" necesita un executor que gestione el ciclo de vida completo de acciones automatizadas despues de que el motor de decision produce una criticidad. Actualmente no existe ning mecanismo para coordinar rotacion de credenciales AWS, reintentos con backoff, notificaciones multi-canal y escalacion a humanos.

## What Changes

- State machine para ciclo de vida de rotacion: PENDING → ROTATING → SUCCESS/FAIL con retry x3
- Implementacion de AWS STS UpdateAccessKey + CreateAccessKey flow con espera de propagacion IAM (60s)
- Notification dispatcher con Strategy Pattern (Slack, email, Jira, SNS)
- Timeout global de 5 minutos y escalacion automatica a humano tras agotar reintentos

## Capabilities

### New Capabilities
- `action-executor`: Executor que gestiona acciones automatizadas (rotacion AWS STS, notificacion multi-canal, escalacion) con ciclo de vida mediante state machine y estrategias de retry/rollback para rotaciones fallidas.

### Modified Capabilities
- *Ninguna — no existen specs modificadas en `openspec/specs/`.*

## Impact

- Nuevo modulo `action-executor` que consume outputs del motor de decision: `{ action: [rotate|notify|escalate], severity, client_id, rationale }`
- Dependencia de AWS STS APIs (UpdateAccessKey, CreateAccessKey) y vault para credenciales admin-read-only
- Integracion con notification channels (Slack webhook, SMTP/SES, Jira API, SNS topic)
- Tabla de audit-trail para registro completo de transiciones del state machine
