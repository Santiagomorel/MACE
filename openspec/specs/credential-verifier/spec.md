## Purpose

Provide credential verification for exposed secrets detected by monitoring systems. Detects cloud provider type, maps accounts, validates credentials using admin read-only access, enumerates permissions, and produces a structured output for risk evaluation.

## Requirements

### Requirement: Provider Detection via Alert Source
El sistema SHALL detectar automaticamente el tipo de proveedor cloud (AWS, Azure, GCP) de una credencial expuesta, utilizando GitGuardian como fuente primaria y heuristica por prefix como fallback.

#### Scenario: Provider detected from GitGuardian response
- **WHEN** GitGuardian incluye `provider` en la alerta normalizada
- **THEN** el verificador usa el provider identificado directamente sin heuristica

#### Scenario: Provider fallback via credential prefix (AWS)
- **WHEN** la credencial expuesta tiene prefix `AKIA` y no se dispone de provider confiable de GitGuardian
- **THEN** el verificador identifica AWS como proveedor y enruta a la ruta de verificacion AWS

#### Scenario: Provider fallback via credential prefix (Azure)
- **WHEN** la credencial expuesta comienza con `eyJ` (JWT base64) y no se dispone de provider confiable de GitGuardian
- **THEN** el verificador identifica Azure AD como proveedor y enruta a la ruta de verificacion Azure

#### Scenario: Provider fallback via credential prefix (GCP)
- **WHEN** la credencial expuesta tiene prefix `AIzaSy` y no se dispone de provider confiable de GitGuardian
- **THEN** el verificador identifica GCP como proveedor y enruta a la ruta de verificacion GCP

### Requirement: Credentification Validation via Admin Credentials for the Client
El sistema SHALL verificar si una credencial expuesta es valida usando las credenciales de admin-read-only del cliente correspondiente, no las credenciales expuestas detectadas.

#### Scenario: Valid credentials verification (AWS)
- **WHEN** la credencial sospechosa tiene provider AWS y el verificador obtiene credenciales admin del account del cliente asociado
- **THEN** el verificador invoca AWS STS GetCallerIdentity con las creds admin para validar si la identity correspondiente existe

#### Scenario: Valid credentials verification (Azure)
- **WHEN** la credencial sospechosa tiene provider Azure AD y el verificador obtiene credenciales admin del account del cliente
- **THEN** el verificador invoca Azure AD Graph API para validar el estado activo de la identity

#### Scenario: Expired credentials detection
- **WHEN** GetCallerIdentity o equivalente retorna error de credencial expirada/invalida
- **THEN** se registra el estado como `INVALID` (credencial no activa) y no se continua con enumeration

### Requirement: Fan-Aut Concurrent Credential Verification with Circuit Breakers
El sistema SHALL ejecutar la verificacion concurrente hacia los proveedores del cliente, incluyendo circuit breakers por proveedor para prevenir cascadas.

#### Scenario: Concurrent fan-out for single alert
- **WHEN** el verificador recibe una alerta de credencial expuesta
- **THEN** inicia verificacion concurrente para cada provider potencial (AWS, Azure, GCP) hasta encontrar una coincidencia valida

#### Scenario: Circuit breaker opens due to repeated failures
- **WHEN** un proveedor cloud supera su umbral de fallos consecutive
- **THEN** el circuito del se abre y la solicitud se devuelve rapidamente sin esperar al provider

#### Scenario: Circuit breaker half-open state
- **WHEN** un circuito abierto ha transcurrido el periodo de cooldown
- **THEN** el verificador envia una solicitud de prueba al proveedor; si exitosa, cierra el circuito; si falla, lo permanece abierto

### Requirement: Verification Result Cache with TTL
El sistema SHALL cache por credencial el resultado de la verificacion con TTL configurable (default 5 minutos).

#### Scenario: Cache hit for previously verified credential
- **WHEN** una credencial expuesta se verifica y la verificacion correspondiente ya existe en cache con menos de 5 minutos de antiguedad
- **THEN** el sistema retorna el resultado cached sin invocar las APIs cloud del provider

#### Scenario: Cache miss or TTL exceeded
- **WHEN** no existe un entry de cache para una credencial, o la entrada tiene mas de 5 minutos de edad
- **THEN** el sistema ejecuta la verificacion fresca y almacena el resultado en cache con TTL de 5 minutos

### Requirement: Client Account Mapping for Exposed Credential
El sistema SHALL mapear una credencial expuesta al account del cliente correspondiente, usando GitGuardian's `account_hint` como fuente primaria e iteracion de accounts del cliente como fallback.

#### Scenario: Account mapping via GitGuardian account_hint
- **WHEN** GitGuardian incluye un `account_hint` confiable (keys ya conocidas publicamente) en la alerta
- **THEN** el verificador usa directamente el account hint para localizar credenciales admin del client

#### Scenario: Account mapping via iterative lookup
- **WHEN** no hay un hint confiable de GitGuardian
- **THEN** el verificador itera los accounts conocidos del cliente en su infraestructura hasta encontrar uno donde la credencial es valida

#### Scenario: Unmapped account marked as unknown
- **WHEN** despues de iterar todos los accounts del cliente, ninguna credencial admin valida para la cuenta correspondiente al identificador de la credencia expuesta
- **THEN** el verificador marca la cuenta como `UNKNOWN` y regresa un empty/marker para que el investigue manual

### Requirement: Approximate Permission Enumeration (AWS) - Allow Minus Deny for All Policies Attached to the Identity
El sistema SHALL generar un matrix de acciones por credencial expuesta enumerando y combinando todos los permisos ALLOW y DENY de todas las policies asociadas a la identidad, restando DENY sobre ALLOW.

#### Scenario: Permission enumeration pipeline execution
- **WHEN** se confirma que la credencial es valida para un account de AWS del cliente
- **THEN** el executor sigue estas fases: (1) GetCallerIdentity → obtiene ARN; (2) ListAttachedUserPolicies + GetUserPolicy (inline) → collects all policies; (3) Parses all JSON statements, accumulate Actions donde Effect = Allow, subtract actions donde Effect = Deny; (4) Construye el action-matrix output

#### Scenario: DENY precedence is respected
- **WHEN** una accion aparece en ALLOW de una policy y DENY en otra
- **THEN** la accion se exclue del matrix final (DENY toma precedence sobre ALLOW conforme a las reglas IAM

#### Scenario: Empty permission set after enumeration
- **WHEN** todas las policies asociadas a la identity no contienen ninguna statement activa
- **THEN** el action-matrix retorne un conjunto vacio y se registra con last_used_date=never

### Requirement: Credential Verification Output Format
El sistema SHALL producir la salida de verificacion en el formato minimo requerido por le motor Drools.

#### Scenario: Successful verification produces complete output
- **WHEN** una credencial es valida y la enumeration termina correctamente
- **THEN** el output contiene: `{ account_id, identity_arn, action-matrix: Set<String>, last_used_date }`

#### Scenario: Failed verification produces minimal output
- **WHEN** una creencial no es valida
- **THEN** el output contiene solo `account_id`, `identity_arn`, estado `INVALID` y `last_used_date=never`

### Requirement: Rate Limit Handling with Exponential Backoff Retry
El sistema SHALL manejar rate limits de las APIs cloud usando retry con backoff exponencial.

#### Scenario: API response returns 429 Too Many Requests
- **WHEN** el verificador recibe un status code HTTP 429 de un proveedor cloud
- **THEN** se aplica backoff exponencial y se reintentar la solicitud

#### Scenario: Retry exhausts all attempts
- **WHEN** despues de todos los intentos agotados, las solicitudes continuas son denegados por rate limiting
- **THEN** el verificador regresa un `RATE_LIMITED` status sin bloquear otros procesos concurrentes
