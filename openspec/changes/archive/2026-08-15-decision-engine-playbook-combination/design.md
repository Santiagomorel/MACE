## Context

Tras la deteccion y verificacion de credenciales expuestas (Cambio: motor-verificacion-credenciales), el sistema debe resolver que nivel de criticidad corresponde y que accion tomar. Esto requiere combinar dos fuentes de informacion conflictivas: playbooks estandar del proveedor cloud y reglas especificas definidas por cada cliente, luego ejecutar las acciones correspondientes (rotar, notificar) o escalar a un humano si la rotacion falla.

**Constraints:**
- Fase 1: unicamente AWS como proveedor para rotacion
- Drools es el motor de evaluacion que se usa para reglas del cliente
- El engine debe cargar reglas por cliente sin reiniciar la aplicacion (hot-reload)
- YAGNI aplicado — solo lo necesario antes de R3

## Goals / Non-Goals

**Goals:**
- Definir los 4 playbooks globales `aws-access-key-exposed`, `aws-session-token-leaked`, `aws-root-credentials-exposed`, `aws-iam-role-assumption-abuse` con schema YAML estandar (schema ver sección Decision 1)
- Combinar `playbook_floor` con `reglas_cliente` usando la formula `max(playbook_floor, reglas_cliente)` donde:
  - `playbook_floor` es el piso minimo de severidad del playbook correspondiente al tipo de credential detectado (AKIA, ASIA, root_)
  - `reglas_cliente` es la criticidad evaluada por las reglas Drools especificas del cliente
- Diferenciar automaticamente AKIA (long-term) vs ASIA (session token) durante discovery para aplicar playbook y acciones correctos
- Integrar ISO compliance tagging (`compliance_tags`) en playbooks → alerts → Drools rules → decision output chain completa
- Cargar dinamicamente archivos `.drl` por cliente sin reiniciar la aplicacion
- Modelo simplificado de ciclo de vida del action executor (PENDING → ROTATING → SUCCESS | FAIL, retry x3) para coordinacion entre modulos
- Soporte multi-tenant en Drools con namespace isolation

**Non-Goals:**
- Implementacion de la GUI para configurar reglas del cliente (R3) — solo definicion de schema
- Reglas dinamicas en otros proveedores (Azure/GCP) — se extiende despues
- Integracion real con Slack/email — solo interface y notification dispatcher abstracto
- Revocar credenciales completamente (R3) — solo rotar

## Decisions

### Decision 1: Global Playbook YAML Schema — 4 AWS Credential Exposure Playbooks

La criticidad final se calcula como el MAXIMO entre el piso del playbook y la evaluacion drools del cliente. Esto asegura que los playbooks establecen un nivel minimo de riesgo pero nunca lo reducen — las reglas del cliente pueden elevarlo solo.

**Schema global de playbook (YAML):**
```yaml
playbook_id: <string>                    # Identificador unico del playbook
version: "1.0.x"                         # Version semver del playbook itself
credential_types:                        # Tipos de credential que cubre este playbook
  - <prefix>                             # AKIA | ASIA | root_ (root account)
severity_floor:                          # Piso minimo por categoria de permiso/impacto
  <category_key>: <CRITICO|ALTO|MEDIA|BAJO>
auto_rotate:
  required: <boolean>                    # true si se debe forzar rotacion automatica
  max_window_mins: <int|null>           # null para tokens STS (TTL nativo es defensa primaria)
actions_on_exposure:                     # Acciones automaticas al activar este playbook
  - action_type: <rotate|notify|escalate|monitor>
    target: <key|token|role|account>
    priority_order: <int>               # Orden de ejecucion si hay multiples
compliance_tags:                         # Referencias ISO para auditabilidad y traceability
  - source: "ISO-27001-A.XX"           # e.g., "ISO 27001-A.9.4.1", "ISO 27017-A.10"
    control_description: "<string>"     # Breve descripcion del control cubierto
credentials_targeted:                   # Lista de credenciales AWS relevantes para este playbook
  - credential_type: <access_key|session_token|root_account_iam_role>
    description: "<string>"
override_allowed_by_client:              # Si el cliente puede ajustar piso (solo hacia arriba)
   can_lower_floor: false               # NUNCA se permite bajar debajo del piso del playbook
conditions:                              # Contexto en el que este playbook aplica
  provider: aws
  detection_source: <webhook|discovery|risk_scanner>  # Fuente de deteccion
```

**Los 4 playbooks globales definidos:**

#### 1. `aws-access-key-exposed` (AKIA — Long-term Access Keys)
```yaml
playbook_id: aws-access-key-exposed
version: "1.0.0"
credential_types:
  - AKIA                                # Prefix de access keys LTA AWS
severity_floor:
  s3_full_access: CRITICO
  s3_read_only: ALTO
  iam_modify: CRITICO
  ec2_instance_control: CRITICO
  cloudwatch_read: MEDIA
  nothing_active: BAJO
auto_rotate:
  required: true
  max_window_mins: 15
actions_on_exposure:
  - action_type: rotate
    target: access_key
    priority_order: 1
  - action_type: notify
    target: account_owner
    priority_order: 2
compliance_tags:
  - source: "ISO 27001-A.9.4.1"
    control_description: "Management of information systems addresses and directory levels"
  - source: "ISO 27001-A.9.2.2"
    control_description: "Registration of user access"
credentials_targeted:
  - credential_type: access_key
    description: "AWS Access Key ID (LTA) with AKIA prefix and associated Secret Access Key"
override_allowed_by_client:
  can_lower_floor: false
conditions:
  provider: aws
  detection_source: webhook
```

#### 2. `aws-session-token-leaked` (ASIA — Session Tokens via STS)
```yaml
playbook_id: aws-session-token-leaked
version: "1.0.0"
credential_types:
  - ASIA                                # Prefix de session tokens temporales AWS STS
severity_floor:
  assumed_role_admin_full_access: CRITICO
  assumed_role_s3_read_write: ALTO
  assumed_role_ec2_manage: ALTO
  assumed_role_read_only: MEDIA
  expired_within_1h: BAJO
auto_rotate:
  required: false                        # No aplica — los tokens STS expiran automaticamente
  max_window_mins: null                 # TTL nativo de STS es la defensa primaria
actions_on_exposure:
  - action_type: monitor
    target: session_token
    priority_order: 1
  - action_type: notify
    target: account_owner
    priority_order: 2
  - action_type: escalate
    target: security_team
    priority_order: 3
compliance_tags:
  - source: "ISO 27017-A.10"
    control_description: "Protection of information in cloud services via session token management"
  - source: "ISO 27001-A.9.4.3"
    control_description: "Management of access rights"
credentials_targeted:
  - credential_type: session_token
    description: "AWS STS temporary credentials (Session Token/Security Token) with ASIA prefix"
override_allowed_by_client:
  can_lower_floor: false
conditions:
  provider: aws
  detection_source: webhook
```

#### 3. `aws-root-credentials-exposed` (root_ — Root Account IAM Role)
```yaml
playbook_id: aws-root-credentials-exposed
version: "1.0.0"
credential_types:
  - root_                               # Credentials del account/root user de AWS
severity_floor:
  ec2_active: CRITICO
  s3_bucket_active: CRITICO
  iam_role_attached: CRITICO
  any_resource_active: CRITICO
  no_resources_active: CRITICO          # Piso CRITICO ABSOLUTO — nunca baja
auto_rotate:
  required: true
  max_window_mins: 15
actions_on_exposure:
  - action_type: rotate
    target: access_key
    priority_order: 1
  - action_type: escalate
    target: senior_security_team
    priority_order: 2
  - action_type: notify
    target: account_owner_mfa_enforced
    priority_order: 3
compliance_tags:
  - source: "ISO 27001-A.9.2.6"
    control_description: "Management of privileged access rights"
  - source: "ISO 27001-A.8.1.1"
    control_description: "Inventory of information and other associated assets"
credentials_targeted:
  - credential_type: root_account_iam_role
    description: "AWS root account credentials — the most privileged identity in the AWS Organization"
override_allowed_by_client:
  can_lower_floor: false
conditions:
  provider: aws
  detection_source: webhook
```

#### 4. `aws-iam-role-assumption-abuse` (AKIA+AssumeRole or ASIA+AssumeRole)
```yaml
playbook_id: aws-iam-role-assumption-abuse
version: "1.0.0"
credential_types:
  - AKIA                                # Long-term key used to assume unauthorized role
  - ASIA                                # Session token misused for role assumption
severity_floor:
  cross_account_assume_untrusted_trust: CRITICO
  admin_role_assumed_from_regular_user: CRITICO
  sensitive_data_role_from_external_entity: CRITICO
  regular_role_from_internal_source_verified: MEDIA
  orphaned_role_no_attached_policies: BAJO
auto_rotate:
  required: false                        # No se rota un IAM Role — es identitario, no una credencial
  max_window_mins: null
actions_on_exposure:
  - action_type: monitor
    target: assumed_role_session
    priority_order: 1
  - action_type: notify
    target: iam_admin_team
    priority_order: 2
  - action_type: escalate
    target: security_investigation_team
    priority_order: 3
compliance_tags:
  - source: "ISO 27001-A.9.4.4"
    control_description: "Management of privileged privileges and separation of duties"
  - source: "ISO 27018-A.10"
    control_description: "Monitoring and auditing access to cloud PII data via IAM roles"
credentials_targeted:
  - credential_type: access_key
    description: "IAM Role assumption source credentials — AKIA keys used for AssumeRole API calls"
  - credential_type: session_token
    description: "Federation session tokens misused to assume unexpected IAM roles (SAML/OIDC)"
override_allowed_by_client:
  can_lower_floor: false
conditions:
  provider: aws
  detection_source: webhook
```

**Diferenciacion AKIA vs ASIA:**
- `AKIA` prefix → Long-term access key — activa los playbooks **aws-access-key-exposed** o **aws-iam-role-assumption-abuse**; auto-rotate obligatorio (`max_window_mins=15`) porque es un secreto persistente
- `ASIA` prefix → Temporary session token via STS AssumeRole/GetSessionToken — activa los playbooks **aws-session-token-leaked** o **aws-iam-role-assumption-abuse**; no aplica auto-rotate porque el TTL nativo de STS (max 1h por defecto, max 36h con MaxSessionDuration) es la defensa primaria; se usa `monitor` + `notify` en su lugar
- Diferenciacion automatica en el action executor basada en `key_id` prefix + `ttl_remaining_seconds` del discoverer: si `ttl_remaining > 0` y expiry < threshold → `ASIA`, sino → `AKIA`

**ISO tagging strategy:**
- Cada playbook incluye `compliance_tags` como array de objetos con `source` (identificador estandar ISO) y `control_description` (descripcion del control cubierto)
- Los tags se propagan automaticamente al alert generado, a la regla Drools auto-generated (`// compliance: source_value`) y al output del decision engine `{ rationale_tags: [...] }`
- Esta trazabilidad permite a cualquier auditor vincular cada alerta a controles ISO especificos sin requerir correlacion manual

### Decision 2: Multi-tenant Drools — KieFileSystem en Memoria (Opcion C)

**Opciones evaluadas:**
- **A. Un solo KieContainer con namespace por tenant**: Simplifica gestion, pero reglas de diferentes clientes pueden colisionar si no se usa `insert(alert).update(fact)` con filtros estrictos. Mayor riesgo de cross-contamination en evaluciones.
- **B. KieContainer por cliente (aislado)**: Aislamiento total, pero overhead significativo al mantener N containers corriendo. Escala mal con decenas de clientes. Se posterga a refactor.
- **C. KieFileSystem en memoria + recargar al vuelo** (OPCION SELECCIONADA): Equilibrio entre aislamiento y eficiencia. Cada vez que una regla cambia, se recompila solo para ese cliente sin reiniciar toda la aplicacion.

**Rationale:** KieFileSystem permite hot-reload de reglas sin reinicio y aislamiento logico por cliente via namespace y fact-tags. Es el enfoque mas usado en Spring Boot + Drools (referencia: Spring DROOLS tutorial). Los `.drl` se almacenan en DB (PostgreSQL BYTEA) o filesystem (`/rules/{tenant}/`).

**Decision de almacenamiento:**
- Primary: PostgreSQL table `client_rules(id, tenant_id, version, drl_content[BYTEA], created_at)`
- Cache en memoria: LRU cache de KieContainer por tenant con TTL 5 min (invalida al detectar cambio en DB)

### Decision 3: Auto-generation — AWS Metadata → Reglas .drl + Global Playbooks (AWS solo R1)

Para cada cliente que entrega credenciales read-only admin a su cuenta AWS, el sistema descubre automaticamente los recursos y genera reglas Drools iniciales en base a los 4 playbooks globales especificados en Decision 1. **El cliente nunca interactua con archivos .drl**.

**Diferenciacion credential types para auto-generation:**
| Prefijo key_id | Tipo AWS | TLL nativo | Playbook asignado | Rotacion auto? |
|---|---|---|---|---|
| `AKIA` | Long-term access key | Ninguna (persiste hasta revocada) | aws-access-key-exposed O aws-iam-role-assumption-abuse | SI — max 15min |
| `ASIA` | Session token temporario | ≤1h default / ≤36h con MaxSessionDuration | aws-session-token-leaked O aws-iam-role-assumption-abuse | NO — TTL STS es defensa primaria |
| `root_` | Root account | Sin expiracion nativa (no expira) | aws-root-credentials-exposed | SI — max 15min + revocacion inmediata |

**Mecanismo de diferenciacion en action executor:**
```javascript
function determineCredentialType(keyId, metadata) {
  if (keyId.startsWith('AKIA') && !metadata.expiresAt) return 'AKIA'; // LTA persistente
  if (keyId.startsWith('ASIA')) return 'ASIA';                        // Token STS temporal
  if (metadata.arn?.includes('root') || metadata.isRootAccount) return 'root_';
  return UNKNOWN;
}

// Routing al playbook correspondiente segun tipo + contexto de deteccion
```

**Pipeline de descubrimiento:**
```
Creds del cliente (read-only admin)
        │
        ▼
┌───────────────────────────────┐
│ AWS API Discovery (R1: AWS)  │
│                               │
│ ListAccessKeys()              │──▶ Keys activas + lastUsed
│ GetPolicyAttachments()        │──▶ Policies asociadas al key
│ GetBucketACLs()               │──▶ Buckets accesibles
│ ListIAMRoles/Policies()       │──▶ Roles y permisos vinculados
│ DescribeEC2Instances()        │──▶ Instancias en cuenta
│ ResourceTags                  │──▶ Tags "prod"/"dev"/env
└───────────────────────────────┘
        │
        ▼
┌───────────────────────────────┐
│ Mapeo a reglas base           │
│ basadas en playbooks          │
│                               │
│ s3_full_access → CRITICO (piso)│
│ iam_modify     → CRITICO (piso)│
| ec2_control    → CRITICO (piso) │
│ nothing_active → BAJO   (piso) │
└───────────────────────────────┘
        │
        ▼
Generar .drl en memoria → guardar en DB → invalidar cache KieContainer
```

**Mapeo automatico de permisos → criticidades:**
| Permiso detectado | Codigo de permiso | Severidad base (playbook floor) | Cliente puede ajustar |
|---|---|---|---|
| S3 Full Access + prod bucket | s3:PutObject, s3:DeleteObject in prod | CRITICO | Si/No |
| S3 Read Only | s3:GetObject only | ALTO | Si/Bajar |
| IAM Modify | iam:CreateUser, iam:AttachRolePolicy | CRITICO | No/Solo subir o mantener |
| EC2 Instance Control | ec2:* on instances | CRITICO | Si/No |
| CloudWatch Read | cloudwatch:GetMetricData | MEDIA | Si/Bajar |
| Nothing active (key > 90 days) | no policies attached | BAJO | No/eliminar regla |

La generacion automatica del .drl se realiza en dos mecanismos (Decision 3):

1. **Pull periodico** cada 3 horas — Redescubre permisos de AKIA keys con `ListAccessKeys()`, sesion tokens con `GetSessionTokenMetadata()`, y roles IAM activos; actualiza reglas si el tipo de credential o su permissions matrix cambio desde la ultima generacion
2. **Boton manual de pull** on-demand sobre los recursos del cliente — Redescubre solo el recurso expuesto sin esperar al schedule periodico
3. **Pull instantaneo al detectar exposicion** por webhook — Al recibir alerta de credencial expuesta, el dispatcher identifica el tipo (AKIA|ASIA|root_) via prefix detection y ejecuta discovery especifico para ese credential type

**Semaforo dedup:**
- Antes de iniciar cualquier pull periodico/instantaneo se adquiere un semaphore (`rule_generation_lock`) con TTL 15 min
- Si otro proceso ya posee el semaphore en los primeros 30s del intervalo actual → salta y espera al siguiente ciclo
- Esto elimina regeneraciones paralelas o duplicadas entre AKIA vs ASIA discovery pipelines concurrentes

**Manejo de credenciales expiradas:**
- Si las AWS creds de lectura del cliente caducan y el discovery falla por acceso denegado (AccessDenied/AWSSTSExpired) → notificar al cliente que debe actualizar sus creds
- No se regeneran reglas si no se pueden obtener los metadatos — estado queda `PENDING: CRED_REFRESH` hasta que el cliente las renueve

### Decision 4: Cliente UI — Nunca tocar archivos .drl

El cliente interactua exclusivamente con una interfaz web donde:
- Ve las reglas auto-generated con los niveles de criticidad sugeridos por el mapeo de playbooks
- **Puede subir** cualquier nivel (ejcrítico ← ALTO) sin validacion adicional
- **Puede bajar** solo si la playbook floor lo permite (no puede bajar debajo del minimo del playbook estandar — ej no reducir S3 Full Access desde CRITICO a MENOR)
- Cada cambio queda registradocomo `manual_override_by_client = true` con timestamp y usuario

El sistema traduce esos ajustes a .drl internamente y recarga el KieContainer correspondiente.

### Decision 5: Conflict Resolution — Priority-Based Drools Agenda

Drools resuelve conflictos automaticamente mediante `salience` (prioridad). Cada regla del cliente se califica con saliencia basica de su severidad:
- CRITICO → salience 100
- ALTO → salience 80
- MEDIA → salience 60
- BAJO → salience 40

En caso de empate, drools evalua por orden cronologico inverso (ultima definicion gana).

**Decision explicita:** Dos reglas para el mismo cliente y misma credencial se resuelve en Drools nativamente via `salience`. No se necesita logica custom.

### Decision 6: Versionado — Rules Always Get Latest, In-Flight Alerts Keep Current Severity

Cuando cambian las reglas activas en production:
- **Alerta en vuelo al momento del cambio**: Se evalua con la version de reglas vigente cuando el webhook se recibio y la verificacion se completo. Su criticidad ya fue calculada y no cambia.
- **Nueva alerta despues del cambio**: Se evalua con las nuevas reglas.

**Sistema de versionado:**
- Cada vez que un cliente actualiza sus reglas, se crea una nueva version (`client_rules.version += 1`)
- Las alertas almacenan `evaluated_rule_version` para auditabilidad
- Validacion automatica del `.drl` antes de activar si compila correctamente

## Risks / Trade-offs

|Risk|Impact|Mitigation|
|---|---|---||
|Cold start de Drools al compilar .drl en memoria|Medio|Lazy load por tenant + cache LRU en memoria con TTL 5 min|
|Reglas no validas en produccion rompen todo el motor|Alto|Validacion `KieContainer.validate()` antes de activar nueva version; rollback automatico a la ultima version valida|
|.drl en DB (BYTEA) lento para archivos grandes|Bajo|Archivos .drl typicalmente < 50KB por cliente — negligible overhead|
|Propagacion de AWS IAM toma hasta ~60 segundos despues de UpdateAccessKey|Alto|Escalar timeout del executor a 2 min minimos antes de crear nueva access key|
|Reintentos fallidos sobrecargan AWS STS APIs|Medio|max 3 reintentos con backoff (10s→30s→60s)|
|Slack/email integrations no implementadas en R1|Bajo|Dispatcher abstracto; se conecta en R2 cuando estan listas|
|.drl cambia durante evaluacion de alerta en vuelo|Bajo|Cada alerta se evalua con la version de reglas vigente al momento de su llegada (timestamped)|

## Migration Plan

Este es parte del segundo modulo critico del sistema. No hay migracion, solo extension del proyecto existente.

1. **Pase 1**: Definir schema de playbooks standard + storage de reglas por cliente (DB)
2. **Pase 2**: Motor de decision — formula `max(playbook_floor, reglas_cliente)` con Drools integration
3. **Pase 3**: Validacion de integridad de criterios entre modulos

## Open Questions

1. **Notificaciones**: ¿Por qué canal (Slack, email, Jira, custom) se debe notificar a cada cliente? Definir `notification_profile` por cliente en R2.
2. **AWS STS UpdateAccessKey timing exacto**: El tiempo de propagacion real despues de poner una access key como INACTIVE puede variar. Se debe validar con pruebas reales de AWS.
3. **Cantidad maxima de reglas Drools que Drools soporta eficientemente**? Evaluar cuando se detecte el problema real en produccion. No hay suficiente info para definir limite hoy.
4. **AWS API quotas y rate limits**: ¿El pull periodico de cada 3 horas + descubrimiento instantaneo cumple dentro de los AWS Service Quotas (ListAccessKeys: 5/s, GetPolicyAttachments: 10/s)?
5. **Politica de "bajar" criticidades del cliente**: NUNCA se permite bajar por debajo del piso del playbook correspondiente — el floor es el minimo absoluto.
6. **Semaforo dedup — TTL ideal**: ¿15 minutos es suficiente para evitar regeneraciones duplicadas entre pull periodico y webhook-triggered?
