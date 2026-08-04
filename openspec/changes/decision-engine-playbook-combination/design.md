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
- **A. Un solo KieContainer con namespace por tenant**: Simplifica geston, pero reglas de diferentes clientes pueden colisionar si no se usa `insert(alert).update(fact)` con filtros estrictos. Mayor riesgo de cross-contamination en evaluciones.
- **B. KieContainer por cliente (aislado)**: Aislamiento total, pero overhead significativo al mantener N containers corriendo. Escala mal con decenas de clientes.
- **C. KieFileSystem en memoria + recargar al vuelo** (OPCION SELECCIONADA): Equilibrio entre aislamiento y eficiencia. Cada vez que una regla cambia, se recompila solo para ese cliente sin reiniciar toda la aplicacion.

**Rationale:** KieFileSystem permite hot-reload de reglas sin reinicio y aislamiento logico por cliente via namespace y fact-tags. Es el enfoque mas usado en Spring Boot + Drools (referencia: Spring DROOLS tutorial). Los `.drl` se almacenan en DB (PostgreSQL BYTEA) o filesystem (`/rules/{tenant}/`).

**Decision de almacenamiento:**
- Primary: PostgreSQL table `client_rules(id, tenant_id, version, drl_content[BYTEA], created_at)`
- Cache en memoria: LRU cache de KieContainer por tenant con TTL 5 min (invalida al detectar cambio en DB)

### Decision 3: Conflict Resolution — Priority-Based Drools Agenda

Drools resuelve conflictos automaticamente mediante `salience` (prioridad). Cada regla del cliente se califica con saliencia basica de su severidad:
- CRITICO → salience 100
- ALTO → salience 80
- MEDIA → salience 60
- BAJO → salience 40

En caso de empate, drools evalua por orden cronologico inverso (ultima definicion gana).

**Decision explicita:** Dos reglas para el mismo cliente y misma credencial se resuelve en Drools nativamente via `salience`. No se necesita logica custom.

### Decision 4: Versionado — Rules Always Get Latest, In-Flight Alerts Keep Current Severity

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
