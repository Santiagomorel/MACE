## Purpose

Execute credential rotation actions (AWS access keys) with state machine lifecycle, retry logic, IAM propagation handling, and multi-channel notification dispatch.

## Requirements

### Requirement: Rotation state machine lifecycle
El action executor SHALL gestionar el ciclo de vida de una rotacion como un estado maquina con los siguientes estados y transiciones:

`PENDING → ROTATING → SUCCESS → NOTIFICAR`

`ROTATING → FAILURE → RETRY(x3) → ESCALATE`

Cada transicion se registra en audit-trail con detalles de timing, resultados individuales por intentos. Un timeout global de 5 minutos cancela automaticamente cualquier rotacion en curso.

#### Scenario: Successful rotation within first attempt
- **WHEN** la accion `rotate` se ejecuta para una credencial valida y el AWS STS UpdateAccessKey tiene exito en su primer intento
- **THEN** la alerta avanza de ROTATING a SUCCESS y luego a NOTIFICAR

#### Scenario: Rotation retries after failure
- **WHEN** la accion `rotate` falla en su primer intento
- **THEN** el executor reintenta con backoff exponencial (10s, 30s, 60s) maximo 3 veces antes de escalar a humano

#### Scenario: Escalation when all retries fail
- **WHEN** los tres reintentos de `rotate` fallan consecutivamente
- **THEN** la alerta se mueve a ESCALATE y se crea un ticket + notificacion para el equipo de respuesta

#### Scenario: Rotation timeout after 5 minutes
- **WHEN** una rotacion permanece en estado ROTATING por mas de 5 minutos sin completarse
- **THEN** el sistema cancela, marca el estado como TIMEOUT y escala a humano automaticamente

### Requirement: Action execution for AWS credential rotation
El sistema SHALL ejecutar la rotacion de access keys de AWS mediante el siguiente flow:
1. Obtener las credenciales admin-read-only del cliente desde vault (usando las credenciales ya verificadas por el verificador)
2. Llamar a AWS STS `UpdateAccessKey` con estado INACTIVE para la clave expuesta
3. Esperar propagacion IAM (max 60 segundos) y verificar que la clave sigue activa con `ListAccessKeys`
4. Si la clave INACTIVA no se propaga, esperar un periodo seguro de propagacion
5. Llamar a AWS STS `CreateAccessKey` para generar una nueva key pair
6. Encriptar las nuevas credenciales y actualizar vault/DB con ellas
7. Registrar todas las acciones en el audit trail del sistema

#### Scenario: Full rotation flow executes successfully
- **WHEN** un acceso de credencial expuesta requiere rotacion automatica
- **THEN** el sistema actualiza la access key a INACTIVE, espera propagacion, crea una nueva par de llaves y las almacena en vault seguro

#### Scenario: Rotation respects 60-second IAM propagation window
- **WHEN** se llama `UpdateAccessKey` con estado INACTIVE para una clave AWS
- **THEN** el sistema espera un periodo minimo de espera antes de crear la nueva access key (al menos 60 segundos)

### Requirement: Notification dispatcher strategy pattern
El action executor SHALL usar un Strategy Pattern para despachar notificaciones a diferentes canales. La interface abstracta expone operaciones `notify(Credential, Severity)` donde cada estrategia implementa canal especifico (Slack, email, Jira, AWS SNS). La estrategia correcta se elige por `client.notification_profile`.

#### Scenario: Slack notification dispatched by default profile
- **WHEN** una alerta atinge la fase de notificacion y el cliente tiene `notification_profile=slack` en su configuracion
- **THEN** el dispatcher usa la estrategia de slack para enviar la notificacion al canal de incidentes del cliente

#### Scenario: Multi-channel notification with custom profile
- **WHEN** un cliente configura `notification_profile=["slack", "email", "jira"]`
- **THEN** el dispatcher ejecuta cada estrategia en paralelo y reporta resultado individual de cada canal
