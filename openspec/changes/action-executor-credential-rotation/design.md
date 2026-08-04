## Context

Tras la decision de criticidad del motor de respuesta, el action executor debe ejecutar acciones automatizadas: rotar credenciales AWS STS, notificar por canales configurados y escalar a humano si falla. El sistema opera unicamente con AWS en R1.

**Constraints:**
- Fase 1: unicamente AWS como proveedor para rotacion
- Propagacion IAM de AWS toma hasta ~60 segundos despues de UpdateAccessKey
- YAGNI aplicado — solo lo necesario antes de R3

## Goals / Non-Goals

**Goals:**
- Implementar state machine para ciclo de vida de rotacion con estados: PENDING → ROTATING → SUCCESS | FAIL
- Executar AWS STS UpdateAccessKey + CreateAccessKey flow en <5 min
- Notificar por canal configurado (Slack, email, Jira, SNS) post-success
- Escalar a humano via ticket+email tras 3 reintentos agotados

**Non-Goals:**
- GUI para configurar reglas del cliente (R3)
- Reglas dinamicas en otros proveedores (Azure/GCP)
- Revocar credenciales completamente (R3) — solo rotar
- Evaluacion de decision (cubre `decision-engine-playbook-combination`)

## Decisions

### Decision 1: State Machine — Sequential with Retry Sub-flow

```
PENDING → ROTATING → SUCCESS → NOTIFICAR ✅
                      ↓
                   TIMEOUT ⏱
ROTATING → FAILURE → RETRY(x3) → ESCALATE 🚨
                    (backoff 10s→30s→60s)
```

- Un `alert_id` avanza por estados fijos deterministas
- Dentro de `ROTATING`, sub-flow de retry x3 es independiente al proceso principal
- **No se implementan causal chains** en R1 — flujo unico sin ramas dependientes

### Decision 2: AWS STS Flow with Propagation Wait

1. Obtener credentials admin-read-only del cliente desde vault
2. Llamar `UpdateAccessKey(INACTIVE)` para la access key expuesta
3. Esperar propagacion IAM (minimo 60 segundos) + verificar inactividad con `ListAccessKeys`
4. Si no propaga, esperar periodo seguro adicional
5. Llamar `CreateAccessKey` para generar nuevo par de llaves
6. Encriptar y almacenar en vault
7. Registrar ciclo completo en audit trail

**Decision sobre timeout:** Timeout global de 5 minutos para el ciclo completo. Escalar timeout del executor a 2 min minimos antes de crear nueva access key despues de UpdateAccessKey.

### Decision 3: Notification Dispatcher — Strategy Pattern

**Interface abstracta:**
```java
interface ActionExecutor {
    ExecutionResult rotate(Credential c);
    void notify(Credential c, Severity severity);
}
```

Implementaciones (interfaces en R1):
- `SlackNotificationService` — Webhook a canal de incidentes
- `EmailNotificationService` — SMTP o AWS SES
- `TicketNotificationService` — Jira/ServiceNow webhooks
- `AwsSnsNotificationService` — SNS topic por cliente

La estrategia correcta se elige por `client.notification_profile`. Concrete implementations se extienden en R2/R3.

## Risks / Trade-offs

|Risk|Impact|Mitigation|
|---|---|---|
|AWS STS propagation >60s|Alto|Escalar timeout a 2min minimos + verificar inactividad real|
|Vault indisponible durante rotacion|Alto|Retry con backoff al vault; fallback manual si falla>10min|
|Slack/email integrations no listas en R1|Bajo|Dispatcher abstracto; conectar en R2|
|Reintentos sobrecargan AWS STS APIs|Medio|max 3 reintentos con backoff (10s→30s→60s)|

## Migration Plan

1. **Pase 1**: State machine core + rotation state definitions
2. **Pase 2**: AWS STS UpdateAccessKey + CreateAccessKey flow completo
3. **Pase 3**: Notification dispatcher abstracto + audit trail
