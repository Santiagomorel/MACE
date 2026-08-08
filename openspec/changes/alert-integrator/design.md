## Context

El sistema "Motor de Respuesta a Exposicion de Credenciales" carece de una capa de ingesta de alertas. Los tres cambios activos (`motor-verificacion-credenciales`, `decision-engine-playbook-combination`, `action-executor-credential-rotation`) asumen que las alertas normalizadas ya existen, pero no han planificado como llegan al sistema.

El requerimiento es claro: las alertas deben llegar en tiempo real via webhooks, sin polling. El primer proveedor es GitGuardian. Se requiere un modelo genérico y un patron de adapter para soportar futuros proveedores.

**Constraints:**
- Webhook ONLY — no polling
- Real-time: la exposicion debe notificarse inmediatamente despues de la deteccion
- Formato generico: el adapter se desacopla del verifier
- Cache de dedup para evitar peticiones duplicadas por la misma deteccion
- Free-tier friendly: preferir caching local (Caffeine) sobre Redis

## Goals / Non-Goals

**Goals:**
- Endpoint webhook REST para ingesta en tiempo real
- Adapter pattern con GitGuardianAdapter como primer adapter
- Modelo de alerta genérico (`GenericAlertModel`) que desacopla el input del verifier
- Deduplicacion en dos niveles: event-level (TTL corto) y secret-level (estado-based)
- Manejo de falsos positivos con cooldowns basados en estado del verifier
- Validacion de autenticidad de webhooks (signature + IP whitelist)
- Worker pool para procesamiento concurrente
- Dead letter queue para alerts fallidos

**Non-Goals:**
- Polling fallback — no se implementa en esta fase (webhook es el unico mecanismo)
- Soporte para batch ingestion (una alerta a la vez, no bulk)
- Alert correlation entre multiples secretos en una misma alerta
- UI para gestion de webhooks — solo API endpoints
- Notificaciones al usuario de DLQ — logs y monitoreo

## Decisions

### Decision 1: Webhook Only (No Polling)
Se selecciona webhook como unico mecanismo de ingestion. No se implementa polling.

**Rationale:** El requerimiento especifica que la informacion de la exposicion debe llegar en el momento en que se detecta. El polling introduce latencia de N minutos que no es aceptable para deteccion de credenciales expuestas.

```
Webhook (real-time)          ──▶  ✅ Inmediato
Polling (cada N min)         ──▶  ❌ Latencia N minutos
```

### Decision 2: Adapter Pattern — Generic Handler + Provider Adapters
Se implementa un handler generico que recibe el webhook, valida la autenticidad, y delega el parseo a un adapter especifico por proveedor.

**Rationale:** GitGuardian es el primer proveedor, pero el diseño debe permitir agregar otros (Snyk, Trivy, etc.) sin modificar el core.

```
┌─────────────────────────────────────────────────────┐
│              Webhook Controller                      │
│                                                      │
│  POST /api/alerts                                    │
│    │                                                 │
│    ├─ Signature validation                           │
│    ├─ IP whitelist                                   │
│    └─ Source detection → Adapter lookup              │
│         │                                            │
│         ├─ GitGuardianAdapter                        │
│         ├─ DefaultAdapter (fallback)                 │
│         └─ [Future: SnykAdapter, TrivyAdapter]       │
└─────────────────────────────────────────────────────┘
```

### Decision 3: Generic Alert Model — Decoupled from GitGuardian
El `GenericAlertModel` es el contrato entre el adapter y el verifier. Incluye `detectedSecret.type` explícito para que el verifier no dependa de heuristica de prefix en el 100% de los casos.

**Rationale:** Actualmente el verifier esta acoplado a GitGuardian (espera hints y prefixes). El modelo genérico permite que el adapter determine el tipo de secret, y si no puede, deja `generic` como fallback.

```
┌─────────────────────────────────────────────┐
│           GenericAlertModel                  │
│                                              │
│  eventId: string                             │
│  source: "gitguardian" | "snyk" | string     │
│  sourceEventId: string                       │
│                                              │
│  detectedSecret: {                           │
│    type: "aws_access_key" | "generic" | ...  │
│    valueHash: string (hashed)                │
│    pattern: string                           │
│  }                                           │
│                                              │
│  context: {                                  │
│    repository: string | null                 │
│    file: string | null                       │
│    commit: string | null                     │
│    line: number | null                       │
│    visibility: "public" | "private"          │
│    foundAt: timestamp                        │
│  }                                           │
│                                              │
│  providerSeverity: string | null             │
│  detectorState: {                            │
│    isNew: boolean                            │
│    previouslyFlagged: boolean                │
│    flagCount: number                         │
│  }                                           │
│                                              │
│  receivedAt: timestamp                       │
│  rawPayload: object (for audit)              │
│                                              │
└─────────────────────────────────────────────┘
```

### Decision 4: Two-Level Deduplication with State
Se implementan dos niveles de deduplicacion que trabajan en conjunto.

**Nivel 1 — Event Dedup (corto):**
- Key: `sourceEventId` (ID original del proveedor)
- TTL: 5 minutos
- Objetivo: evitar que retries del proveedor generen procesamiento duplicado del mismo evento

**Nivel 2 — Secret Dedup (largo, estado-based):**
- Key: `valueHash + repository`
- TTL: configurable, depende del estado previo del verifier
- No es ciego — consulta el resultado del procesador anterior:
  - `false_positive` → cooldown largo (24h)
  - `true_positive` + action completada → cooldown medio (1h)
  - `in_progress` → skip inmediato

```
Alerta entrante
    │
    ▼
┌────────────────────────┐
│ Nivel 1: Event Dedup    │  TTL: 5min
│ Key: sourceEventId      │
│ Ya existe? → SKIP       │
└───────────┬────────────┘
            │ Nuevo evento
            ▼
┌──────────────────────────────────────┐
│ Nivel 2: Secret Dedup                │  TTL: variable
│ Key: valueHash + repository          │
│                                      │
│ ¿Ya existe en cache?                  │
│   ├─ false_positive → cooldown 24h  │
│   ├─ true_positive + done → cooldown│
│   │   1h                              │
│   ├─ in_progress → skip              │
│   └─ no existe → procesar           │
└──────────────────────────────────────┘
```

### Decision 5: Cache Implementation — Caffeine (Local) for Phase 1
Se utiliza Caffeine (in-memory cache de Java) para el dedup en fase 1, con opcion de migrar a Redis si se necesita multi-instancia.

**Rationale:** En fase 1 se espera una sola instancia del servicio. Caffeine es ligero, no requiere infraestructura adicional, y cumple con el constraint de free-tier.

**Riesgo:** Si en el futuro se despliegan multiples instancias, el dedup no se comparte entre ellas. En ese momento se migra a Redis.

### Decision 6: Worker Pool with Reactive Processing
Se utiliza un worker pool de tamaño fijo (configurable, default 5 workers) para procesar alerts despues del dedup.

**Rationale:** Un pool de tamaño fijo con cola de respaldo maneja picos sin saturar el sistema. Cada worker ejecuta el pipeline: normalize → verify → (output to decision engine).

```
Webhook → [Event Dedup] → [Secret Dedup] → [Worker Queue] → Workers → Verifier
                                                          │
                                                          ├─ Worker 1
                                                          ├─ Worker 2
                                                          ├─ Worker 3
                                                          └─ Worker 4
```

### Decision 7: Dead Letter Queue
Se implementa una DLQ simple basada en una tabla de DB (alert_dlq) para alerts que fallan.

**Rationale:** Tabla de DB es mas simple que implementar un sistema de mensajeria dedicado para fase 1. Contiene el raw payload, error, timestamp, y un campo de retry count.

### Decision 8: Webhook Validation Strategy
Se implementan dos capas de validacion de autenticidad:

1. **Signature validation:** HMAC-SHA256 con header `X-GitGuardian-Signature` (o equivalente configurable por proveedor)
2. **IP whitelist:** lista configurable de IPs permitidas por proveedor

**Rationale:** GitGuardian firma sus webhooks y tiene un rango fijo de IPs. Ambas capas son defense-in-depth.

## Risks / Trade-offs

|Risk|Impact|Mitigation|
|---|---|---|
|Dedup local (Caffeine) no comparte entre instancias|Medio|Migrar a Redis cuando se necesiten multiples instancias|
|Falsos positivos de GitGuardian generan cooldowns largos|Bajo|Los cooldowns son configurables; FP reales se benefician del cooldown largo|
|Worker pool saturado bajo picos de alerts|Medio|Queue con backpressure + DLQ para alerts que exceden timeout de espera|
|Raw payload en DLQ crece rapidamente|Bajo|TTL en la tabla DLQ (ej: 7 dias) con cleanup automatico|
|Adapter acoplado a la interfaz de GenericAlertModel|Bajo|Interfaz clara `ProviderAdapter.toGenericAlert(rawPayload)` como contrato|
|Secret valueHash — hash del secreto en cache|Bajo|No se almacena el valor en claro, solo el hash. El hash es unidireccional (SHA-256).|

## Migration Plan

1. Desplegar modulo `alerting` como parte del servicio existente
2. Configurar el endpoint webhook con las credenciales de firma de GitGuardian
3. Registrar `GitGuardianAdapter` en el `AdapterRegistry`
4. Configurar TTLs de dedup (5min event, 24h FP cooldown, 1h TP cooldown)
5. Validar ingesta de alerts de prueba contra el verifier
6. Monitorear dedup hit rate y worker pool utilization en produccion inicial

## Dependencies

- **Credential Verifier** (`motor-verificacion-credenciales`): ingesta el `GenericAlertModel` normalizado y produce el resultado que alimenta el secret-level dedup
- **Drools Engine** (`decision-engine-playbook-combination`): consume el output del verifier, que a su vez consume la alerta del alert-ingestion
- **Credential Rotation** (`action-executor-credential-rotation`): consume el output del decision engine

## Open Questions

1. **GitGuardian webhook signature format exacto:** Confirmar el header y algoritmo exacto que GitGuardian usa para firmar los webhooks (documentacion de su API v2)
2. **TTL de cooldown para true_positive:** 1 hora es suficiente? Podria ser que el usuario rote una key y GitGuardian la re-detecte en el history en < 1h?
3. **Notificacion de DLQ:** Como se notifica al equipo cuando hay alerts en la DLQ? Email? Slack? Log alert?
