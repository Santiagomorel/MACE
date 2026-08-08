## ADDED Requirements

### Requirement: Real-Time Webhook Ingestion Endpoint
El sistema SHALL exponer un endpoint webhook REST `POST /api/alerts` para recibir alertas de deteccion de credenciales expuestas en tiempo real desde proveedores externos.

#### Scenario: Valid webhook alert received
- **WHEN** un proveedor externo envia un POST a `/api/alerts` con payload valido y signature autenticada
- **THEN** el sistema parsea el payload, lo normaliza al `GenericAlertModel`, y lo enruta al worker pool para procesamiento

#### Scenario: Invalid webhook signature rejected
- **WHEN** un POST a `/api/alerts` recibe un payload cuya signature no coincide con el secreto compartido
- **THEN** el sistema responde con HTTP 401 y registra el evento como intento de autentificacion fallida en los logs de auditoria

#### Scenario: Request from untrusted IP rejected
- **WHEN** un POST a `/api/alerts` viene de una IP que no esta en la lista de IPs permitidas del proveedor
- **THEN** el sistema responde con HTTP 403 y registra el evento en los logs de seguridad

### Requirement: Generic Alert Model
El sistema SHALL utilizar un modelo de alerta genérico (`GenericAlertModel`) que abstrae el formato de cualquier proveedor de deteccion de secretos.

#### Scenario: GitGuardian alert mapped to GenericAlertModel
- **WHEN** el GitGuardianAdapter recibe un payload de GitGuardian API v2
- **THEN** mapea los campos al `GenericAlertModel` estableciendo `source: "gitguardian"`, `detectedSecret.type` basado en la deteccion de GitGuardian, y `context` con repo, file, commit, line

#### Scenario: Unknown provider alert with generic type
- **WHEN** un adapter desconoce el tipo de secret o no puede determinarlo
- **THEN** establece `detectedSecret.type: "generic"` y el verifier puede aplicar heuristica de fallback por prefix

#### Scenario: Alert includes provider severity
- **WHEN** el proveedor incluye un nivel de severidad en su payload original
- **THEN** el campo `providerSeverity` se copia al modelo genérico sin transformar (se normaliza despues en el pipeline)

### Requirement: Adapter Pattern for Provider Integration
El sistema SHALL implementar un patron de adapter con un handler genérico y adapters especificos por proveedor, permitiendo agregar nuevos proveedores sin modificar el core.

#### Scenario: GitGuardian adapter processes incoming alert
- **WHEN** llega un webhook de GitGuardian al handler generico
- **THEN** el handler identifica el adapter por el `source` del payload y delega al `GitGuardianAdapter` para el mapeo especifico

#### Scenario: New provider adapter registered without modifying core
- **WHEN** se necesita agregar un nuevo proveedor (ej: Snyk)
- **THEN** se crea un nuevo adapter que implementa la interfaz `ProviderAdapter` y se registra en el adapter registry sin modificar el handler generico

#### Scenario: Adapter registry fallback to default
- **WHEN** el handler no encuentra un adapter registrado para un `source` especifico
- **THEN** utiliza un `DefaultAdapter` que realiza parseo basico y deja el tipo como "generic" para inferencia posterior

### Requirement: Event-Level Deduplication
El sistema SHALL deduplicar eventos entrantes basandose en el event_id del proveedor con un TTL corto configurable (default 5 minutos).

#### Scenario: Duplicate event within TTL discarded
- **WHEN** llega un webhook con un `sourceEventId` que ya existe en el cache de dedup con menos de 5 minutos de antiguedad
- **THEN** el sistema descarta el evento como duplicado y responde con HTTP 200 (sin error) sin enviarlo al worker pool

#### Scenario: New event accepted even for previously seen secret
- **WHEN** llega un webhook con un `sourceEventId` nuevo aunque el secret ya fue reportado previamente
- **THEN** el sistema lo procesa normalmente (el dedup de eventos evita retries del mismo evento, no el re-scaneo de un mismo secret)

### Requirement: Secret-Level Deduplication with State
El sistema SHALL deduplicar alertas de un mismo secret basandose en un hash del secret + repository, consultando el estado previo del verifier para determinar el cooldown.

#### Scenario: Secret re-detected after false positive — long cooldown
- **WHEN** el mismo secret (mismo hash + repo) ya fue procesado anteriormente y el resultado del verifier fue `false_positive`
- **THEN** el sistema aplica un cooldown largo (default 24 horas) sin enviarlo al worker pool

#### Scenario: Secret re-detected after true positive with completed action
- **WHEN** el mismo secret fue procesado y el resultado fue `true_positive` con una accion completada (rotate/notify exitosa)
- **THEN** el sistema aplica un cooldown medio (default 1 hora) antes de volver a procesar

#### Scenario: Secret currently being processed — skipped
- **WHEN** el mismo secret ya tiene un proceso activo en el worker pool (estado: `in_progress`)
- **THEN** el sistema descarta el evento duplicado para evitar procesamiento concurrente del mismo secret

### Requirement: Webhook Authenticity Validation
El sistema SHALL validar la autenticidad de cada webhook recibido mediante signature verification y opcionally IP whitelist.

#### Scenario: Signature validation with shared secret header
- **WHEN** el webhook incluye un header de signature (ej: `X-GitGuardian-Signature`)
- **THEN** el sistema calcula HMAC-SHA256 del payload con el secreto compartido y compara con el header

#### Scenario: IP whitelist validation for GitGuardian
- **WHEN** el webhook proviene de una IP
- **THEN** el sistema verifica que la IP esta en la lista de IPs autorizadas del proveedor

### Requirement: Worker Pool for Concurrent Alert Processing
El sistema SHALL utilizar un worker pool con tamaño configurable para procesar alertas normalizadas de forma concurrente.

#### Scenario: Concurrent processing of multiple alerts
- **WHEN** llegan multiples alerts en un corto intervalo de tiempo
- **THEN** el worker pool las procesa en paralelo dentro del limite de workers configurados

#### Scenario: Worker pool backpressure
- **WHEN** la cola de alerts supera el tamano del worker pool
- **THEN** las alerts adicionales se encolan y se procesan cuando un worker se libera

### Requirement: Dead Letter Queue for Failed Alerts
El sistema SHALL mantener una dead letter queue (DLQ) para alerts que fallan en el parsing, validacion o procesamiento.

#### Scenario: Invalid format alert sent to DLQ
- **WHEN** un webhook llega con un formato que no puede ser parseado por ningun adapter
- **THEN** el sistema registra el raw payload en la DLQ con un error descriptivo y responde con HTTP 200 (sin reintentos automaticos)

#### Scenario: Processing failure alert sent to DLQ
- **WHEN** una alert pasa la validacion pero falla durante el procesamiento por el worker
- **THEN** el sistema intenta un reintent (max 3 intentos) y si todos fallan, mueve la alert a la DLQ

### Requirement: Alert Processing Pipeline
El sistema SHALL ejecutar un pipeline de procesamiento: parse → validar autenticidad → dedup event → dedup secret → normalizar → enviar al worker pool → procesar con verifier.

#### Scenario: Successful alert processing flow
- **WHEN** una alert pasa todos los pasos del pipeline sin errores
- **THEN** el sistema envia el `GenericAlertModel` normalizado al modulo de verificacion (credential-verifier) para evaluar la credencial

## MODIFIED Requirements

### Requirement: Credential Verifier Input Accepts Generic Alert Model (MODIFIED from `motor-verificacion-credenciales`)
El sistema del verificador de credenciales SHALL aceptar alerts en formato `GenericAlertModel` en lugar de un formato especifico de GitGuardian.

#### Scenario: Verifier processes alert with explicit secret type
- **WHEN** el verificador recibe un `GenericAlertModel` donde `detectedSecret.type` es `"aws_access_key"`
- **THEN** el verificador enruta a la ruta de verificacion AWS directamente sin necesidad de heuristica de prefix

#### Scenario: Verifier handles generic type with heuristic fallback
- **WHEN** el verificador recibe un `GenericAlertModel` donde `detectedSecret.type` es `"generic"`
- **THEN** el verificador aplica heuristica por prefix (AKIA, eyJ, AIzaSy) como fallback para determinar el proveedor

#### Scenario: Verifier ignores GitGuardian-specific fields
- **WHEN** el verificador recibe un `GenericAlertModel`
- **THEN** NO depende de campos especificos de GitGuardian (como `account_hint`), sino que usa los campos genericos del modelo
