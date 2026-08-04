## 1. State Machine Core — Rotation Lifecycle

- [ ] 1.1 Definir enum `RotationState` con valores: PENDING, ROTATING, SUCCESS, FAIL, ESCALATE, TIMEOUT
- [ ] 1.2 Implementar clase `RotationStateMachine` con metodos para transiciones entre estados
- [ ] 1.3 Implementar validacion de transiciones permitidas (PENDING→ROTATING, ROTATING→SUCCESS|FAIL, etc.)
- [ ] 1.4 Implementar timeout de 5 minutos global con timer/cancelable task
- [ ] 1.5 Implementar logica de retry x3 con backoff exponencial (10s→30s→60s) dentro de ROTATING→FAILURE sub-flow

## 2. AWS STS Flow — Credential Rotation Execution

- [ ] 2.1 Obtener credenciales admin-read-only del cliente desde vault usando las credenciales verificadas por el verificador
- [ ] 2.2 Implementar AWS STS `UpdateAccessKey` con estado INACTIVE para la access key expuesta
- [ ] 2.3 Implementar espera de propagacion IAM (minimo 60 segundos) + verificar inactividad con `ListAccessKeys`
- [ ] 2.4 Implementar logica de fallback: si no propaga, esperar periodo seguro adicional antes de continuar
- [ ] 2.5 Implementar AWS STS `CreateAccessKey` para generar nuevo par de llaves
- [ ] 2.6 Encriptar y almacenar nuevas credenciales en vault DB
- [ ] 2.7 Registrar todas las acciones individuales en el audit trail (timing, resultados por intento)

## 3. Notification Dispatcher — Strategy Pattern

- [ ] 3.1 Definir interface abstracta `ActionExecutor` con metodos `rotate(Credential)` y `notify(Credential, Severity)`
- [ ] 3.2 Implementar `SlackNotificationService` como estrategia de notificacion webhook a canal de incidentes
- [ ] 3.3 Implementar `EmailNotificationService` como estrategia SMTP/AWS SES
- [ ] 3.4 Implementar `TicketNotificationService` como estrategia Jira/ServiceNow webhooks para escalacion
- [ ] 3.5 Implementar `AwsSnsNotificationService` como estrategia SNS topic por cliente
- [ ] 3.6 Implementar `NotificationDispatcherStrategy` que elija la implementacion correcta por `client.notification_profile`
- [ ] 3.7 Soportar notification_profile multi-canal (array de canales) ejecutando estrategias en paralelo con resultados individuales

## 4. Audit Trail — Logging & Tracking

- [ ] 4.1 Definir schema/DTO para registracion completa de transiciones del state machine
- [ ] 4.2 Implementar logging de cada estado al entrar/salir con timestamp, duracion, resultados por intento
- [ ] 4.3 Grabar escalacion automatica a humano (ticket + email) en audit trail cuando fallan 3 reintentos
- [ ] 4.4 Grabar timeout como estado TIMEOUT cuando se exceden los 5 minutos maximo

## 5. Integration — Wire Together Components

- [ ] 5.1 Integrar state machine con execution pipeline: receive `{ action: rotate, client_id, credential }` input
- [ ] 5.2 Conectar rotation success/failure results con notification dispatcher post-success
- [ ] 5.3 Implementar escalacion automatica a humano (ticket + email) cuando se agotan los 3 reintentos
- [ ] 5.4 Agregar timeout de 5 minutos como cancelador del entire pipeline
- [ ] 5.5 Validar que toda la ejecucion se registra en audit trail completo

## 6. Tests & Validation

- [ ] 6.1 Test unitario: transicion exitosa PENDING→ROTATING→SUCCESS dentro de 5 min
- [ ] 6.2 Test unitario: retry con backoff exponencial tras falla en ROTATING→FAILURE
- [ ] 6.3 Test unitario: escalacion automatica al humano tras 3 reintentos agotados
- [ ] 6.4 Test unitario: timeout a TIMEOUT cuando supera 5 minutos
- [ ] 6.5 Test unitario: propagacion IAM se respeta (espera ≥60s)
- [ ] 6.6 Test unitario: notification dispatcher elige estrategia correcta por client_profile
- [ ] 6.7 Test de integracion: full flow end-to-end (rotation + notify + audit trail)
