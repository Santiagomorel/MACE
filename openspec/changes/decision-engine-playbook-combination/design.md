## Context

Tras la deteccion y verificacion de credenciales expuestas (Cambio: motor-verificacion-credenciales), el sistema debe resolver que nivel de criticidad corresponde y que accion tomar. Esto requiere combinar dos fuentes de informacion conflictivas: playbooks estandar del proveedor cloud y reglas especificas definidas por cada cliente, luego ejecutar las acciones correspondientes (rotar, notificar) o escalar a un humano si la rotacion falla.

**Constraints:**
- Fase 1: unicamente AWS como proveedor para rotacion
- Drools es el motor de evaluacion que se usa para reglas del cliente
- El engine debe cargar reglas por cliente sin reiniciar la aplicacion (hot-reload)
- YAGNI aplicado — solo lo necesario antes de R3

## Goals / Non-Goals

**Goals:**
- Combinar `playbook_floor` con `reglas_cliente` usando la formula `max(playbook_floor, reglas_cliente)` donde:
  - `playbook_floor` es el piso minimo de severidad del playbook estandar (ej: CRITICO para S3 + IAM)
  - `reglas_cliente` es la criticidad evaluada por las reglas Drools especificas del cliente
- Cargar dinamicamente archivos `.drl` por cliente sin reiniciar la aplicacion
- Definir schema de playbooks standard en formato YAML/JSON
- Modelo simplificado de ciclo de vida del action executor (PENDING → ROTATING → SUCCESS | FAIL, retry x3) para coordinacion entre modulos
- Soporte multi-tenant en Drools con namespace isolation

**Non-Goals:**
- Implementacion de la GUI para configurar reglas del cliente (R3) — solo definicion de schema
- Reglas dinamicas en otros proveedores (Azure/GCP) — se extiende despues
- Integracion real con Slack/email — solo interface y notification dispatcher abstracto
- Revocar credenciales completamente (R3) — solo rotar

## Decisions

### Decision 1: Fomula de Combinacion = max(playbook_floor, reglas_cliente)

La criticidad final se calcula como el MAXIMO entre el piso del playbook y la evaluacion drools del cliente. Esto asegura que los playbooks establecen un nivel minimo de riesgo pero nunca lo reducen — las reglas del cliente pueden elevarlo solo.

**Formato de playbook (YAML):**
```yaml
playbook: aws-access-key-exposed
conditions:
  provider: aws
  credential_type: access_key
severity_floor:
  s3_full_access: CRITICO
  s3_read_only: ALTO
  iam_modify: CRITICO
  ec2_instance_control: CRITICO
  nothing_active: BAJO
override_allowed_by_client: true
auto_rotate:
  required: true
  max_window_mins: 15
```

### Decision 2: Multi-tenant Drools — KieFileSystem en Memoria (Opcion C)

**Opciones evaluadas:**
- **A. Un solo KieContainer con namespace por tenant**: Simplifica gestion, pero reglas de diferentes clientes pueden colisionar si no se usa `insert(alert).update(fact)` con filtros estrictos. Mayor riesgo de cross-contamination en evaluciones.
- **B. KieContainer por cliente (aislado)**: Aislamiento total, pero overhead significativo al mantener N containers corriendo. Escala mal con decenas de clientes. Se posterga a refactor.
- **C. KieFileSystem en memoria + recargar al vuelo** (OPCION SELECCIONADA): Equilibrio entre aislamiento y eficiencia. Cada vez que una regla cambia, se recompila solo para ese cliente sin reiniciar toda la aplicacion.

**Rationale:** KieFileSystem permite hot-reload de reglas sin reinicio y aislamiento logico por cliente via namespace y fact-tags. Es el enfoque mas usado en Spring Boot + Drools (referencia: Spring DROOLS tutorial). Los `.drl` se almacenan en DB (PostgreSQL BYTEA) o filesystem (`/rules/{tenant}/`).

**Decision de almacenamiento:**
- Primary: PostgreSQL table `client_rules(id, tenant_id, version, drl_content[BYTEA], created_at)`
- Cache en memoria: LRU cache de KieContainer por tenant con TTL 5 min (invalida al detectar cambio en DB)

### Decision 3: Auto-generation — AWS Metadata → Reglas .drl (AWS solo R1)

Para cada cliente que entrega credenciales read-only admin a su cuenta AWS, el sistema descubre automaticamente los recursos y genera reglas Drools iniciales en base a lo que se encuentra. **El cliente nunca interactua con archivos .drl**.

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
│ ec2_control    → ALTO   (piso) │
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
| EC2 Instance Control | ec2:* on instances | ALTO | Si/No |
| CloudWatch Read | cloudwatch:GetMetricData | MEDIA | Si/Bajar |
| Nothing active (key > 90 days) | no policies attached | BAJO | No/eliminar regla |

La generacion automatica del .drl se realiza en dos mecanismos:

1. **Pull periodico** cada 3 horas (scheduled job): Descubre cambios en los recursos del cliente y regenera reglas si detecta diferencias con la version actual
2. **Boton manual de pull**: El cliente puede disparar un descubrimiento on-demand desde la UI
3. **Pull instantaneo al detectar exposicion**: Cuando el webhook recibe una alerta de credencial expuesta, se hace un pull inmediato solo para ese recurso afectado

**Semaforo dedup:**
- Antes de iniciar cualquier pull periodico/instantaneo se adquiere un semaphore (`rule_generation_lock`) con TTL 15 min
- Si otro proceso Ya posee el semaphore en los primeros 30s del intervalo actual → salto y espera al siguiente ciclo
- Esto elimina regeneraciones paralelas o duplicadas entre el pull periodico y el webhook-triggered

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
5. **Politica de "bajar" criticidades del cliente**: ¿Un cliente puede bajar S3 Full Access de CRITICO a ALTO o el playbook floor debe ser infranqueable? Decision actual: no por debajo del piso del playbook (CRITICO), si pero con nota obligatoria.
6. **Semaforo dedup — TTL ideal**: ¿15 minutos es suficiente para evitar regeneraciones duplicadas entre pull periodico y webhook-triggered?
