## Purpose

Define how the decision engine computes criticality by combining standard playbook floors with client-specific Drools rules, handles credential type differentiation (AKIA, ASIA, root_), and integrates with the alert-to-action pipeline.

## ADDED Requirements

### Requirement: Decision engine computes max(playbook_floor, reglas_cliente)
El motor de decision SHALL calcular la criticidad final aplicando la formula `max(playbook_floor, reglas_cliente)` donde:
- `playbook_floor` es el piso minimo de severidad derivado del playbook estandar basado en los permisos detectados (action-permission matrix del verificador)
- `reglas_cliente` es la severidad calculada por las reglas Drools especificas del cliente

El playbook establece un piso minimo: la criticidad final nunca puede ser MENOR que el playbook floor. Solo puede ser igual o MAYOR si las reglas del cliente elevan la severidad.

#### Scenario: Criticality elevation by rules
- **WHEN** `playbook_floor` = ALTO y `reglas_cliente` = CRITICO
- **THEN** la criticidad final = CRITICO

#### Scenario: Criticality stays at playbook floor
- **WHEN** `playbook_floor` = CRITICO y `reglas_cliente` = BAJO
- **THEN** la criticidad final = CRITICO

#### Scenario: Equal severity
- **WHEN** `playbook_floor` = ALTO y `reglas_cliente` = ALTO
- **THEN** la criticidad final = ALTO

#### Scenario: Severe action elevates criticality
- **WHEN** `playbook_floor` = MEDIA (EC2_read_only) y `reglas_cliente` = CRITICO (s3_write + ec2_modify detected by rules)
- **THEN** la criticidad_final = CRITICO

#### Scenario: Critical criticality floors remain critical
- **WHEN** `playbook_floor` = CRITICO y `reglas_cliente` = MEDIA
- **THEN** la criticidad_final = CRITICO (el piso critico se mantiene)

### Requirement: Playbooks standard are defined in YAML/JSON format
El sistema SHALL definir playbooks estandar como archivos estructurados que mapean combinaciones de tipos de credential y permisos a niveles minimos de severidad. El formato base es YAML/JSON con los siguientes campos obligatorios: `playbook_id`, `conditions` (provider, detection_source), `credential_types` (lista de prefixes AKIA|ASIA|root_), `severity_floor` (mapping de acciones a severidades), `auto_rotate.require`, `auto_rotate.max_window_mins`, `actions_on_exposure` (lista ordenada por priority_order), `compliance_tags` (array source+description), y `credentials_targeted` (lista con description). El campo `credential_types` es obligatorio para que el engine sepa cual playbook aplicar a cada tipo de credential detectado.

#### Scenario: Playbook defines severity for AKIA access key
- **WHEN** un secreto expuesto tiene `key_id` con prefix `AKIA` y discovery muestra S3 Full Access en cuentas del cliente
- **THEN** el motor carga el playbook `aws-access-key-exposed`, asigna piso CRITICO, y ejecuta acciones [rotate (p1) → notify (p2)] dentro ventana 15 min

#### Scenario: Playbook for ASIA session token applies no rotation window
- **WHEN** un secreto expuesto tiene `key_id` con prefix `ASIA` y discovery confirma que es un token temporal STS TTL=30min
- **THEN** el motor carga el playbook `aws-session-token-leaked`, asigna piso segun permisos del session, ejecuta acciones [monitor (p1) → notify (p2)] sin ventana de rotacion

#### Scenario: Playbook for root credentials always applies CRITICO floor
- **WHEN** un secreto expuesto es identificado como credencial de cuenta/root (`credential_type=root_account_iam_role`)
- **THEN** el motor carga el playbook `aws-root-credentials-exposed`, asigna piso CRITICO ABSOLUTO (sin excepcion), ejecuta acciones [rotate p1 → escalate senior p2 → notify MFA p3]

#### Scenario: Playbook for IAM role assumption abuse differentiates credential type
- **WHEN** se detecta una suposicion de rol IAM desde fuente no autorizada y `key_id` prefix es AKIA o ASIA
- **THEN** el motor carga el playbook `aws-iam-role-assumption-abuse`, evalua piso segun políticas del role asumido, ejecuta acciones [monitor → notify IAM admin → escalate investigation]

#### Scenario: Credential type AKIA triggers mandatory rotation
- **WHEN** la deteccion identifica un secreto con `key_id` prefix `AKIA`
- **THEN** el action executor aplica obligatoriamente rotacion de clave en ≤15 minutos (auto_rotate.required=true, max_window_mins=15)

#### Scenario: Credential type ASIA triggers monitor-only for session tokens
- **WHEN** la deteccion identifica un secreto con `key_id` prefix `ASIA`
- **THEN** el actionexecutor aplica monitor + notify en lugar de rotacion porque TTL nativo de STS es la defensa primaria (auto_rotate.required=false)

#### Scenario: Credential type root always escalates to senior team
- **WHEN** la deteccion identifica credencial de cuenta como expuesta (`credential_type=root_account_iam_role`)
- **THEN** el action executor escala obligatoriamente al equipo senior de seguridad (segunda accion del playbook aws-root-credentials-exposed) independientemente de los permisos activos

### Requirement: Credential type differentiation (AKIA, ASIA, root_)
El sistema SHALL diferenciar automaticamente cada credencial detectada en tres tipos segun su prefix AWS: AKIA (long-term access key), ASIA (session token temporary via STS), y root_ (account root credentials). Esta diferenciacion determina cual de los 4 playbooks globales se activa y que acciones aplica. La diferenciacion se realiza combinando `key_id` prefix detection con `ttl_remaining_seconds` del discoverer service.

#### Scenario: AKIA key identification and playbook routing
- **WHEN** una credencial expuesta tiene metadata `key_id = "AKIAIOSFODNN7EXAMPLE"` y `ttl_remaining > 0` (no expira)
- **THEN** el sistema clasifica como tipo AKIA, carga playbook `aws-access-key-exposed`, activa auto_rotate obligatorio

#### Scenario: ASIA token identification and playbook routing
- **WHEN** una credencial expuesta tiene metadata `key_id = "ASIAIOSFODNN7EXAMPLE"` y `ttl_remaining < expiry_threshold` (expira pronto)
- **THEN** el sistema clasifica como tipo ASIA, carga playbook `aws-session-token-leaked`, aplica solamente monitor + notify

#### Scenario: Root credential identification triggers highest playbook
- **WHEN** una credencial expuesta tiene metadata que indica account principal o root ARN ("root" en la cuenta)
- **THEN** el sistema clasifica como tipo root_, carga playbook `aws-root-credentials-exposed` con piso CRITICO ABSOLUTO sin excepcion

#### Scenario: Unknown prefix defaults to no playbook match
- **WHEN** una credencial expuesta tiene un prefix de key_id que no coincide con AKIA, ASIA ni root_ patterns reconocidos
- **THEN** el motor registra el evento sin asignar playbook global y envia a review manual (sin automatizacion de respuesta)

### Requirement: ISO compliance tagging propagation
Cada playbook gloal incluye `compliance_tags` como array de objetos con `source` (identificador estandar ISO 27001/27017/27018) y `control_description`. El sistema SHALL propagar estos tags automaticamente a lo largo del pipeline: al alert generado, a la regla Drools auto-generated (como comentario en el .drl), y al output del decision engine. Esto permite a cualquier auditor vincular cada incidente a controles ISO especificos sin correlacion manual.

#### Scenario: ISO tags propagate from playbook to alert
- **WHEN** un alert se crea basado en el playbook `aws-access-key-exposed`
- **THEN** el alert incluye `compliance_tags` del playbook como campo directo (e.g., `compliance_tags[0].source = "ISO 27001-A.9.4.1"`)

#### Scenario: ISO tags propagate from playbook to Drools rule generation
- **WHEN** el auto-generator crea un .drl desde los playbooks para un cliente
- **THEN** las directivas compliance se insertan como comentarios en el `.drl` inicial (e.g., `// compliance: ISO 27017-A.10 - Protection of information in cloud services`)

#### Scenario: ISO tags included in decision engine output sent to action executor
- **WHEN** el decision engine envia su output al pipeline de acciones
- **THEN** incluye `rationale_tags` con los compliance_tags del playbook usado (e.g., `{ rationale, playbook_id, playbook_compliance_tags: [{"source": "...", "description": "..."}] }`)

### Requirement: Drools multi-tenant dynamic loading
El sistema SHALL cargar reglas Drools (.drl) dinamicamente por cliente sin reiniciar la aplicacion. Un archivo .drl se valida con `KieContainer.validate()` antes de activar, y se almacena en PostgreSQL con versionado (`client_rules` table with auto-incrementing version). Se cachea el KieContainer construido por tenant con invalidation al detectar cambio en DB. Cada regla incluye un campo `salience` determinado por su severidad (CRITICO=100, ALTO=80, MEDIA=60, BAJO=40).

#### Scenario: Dynamic rule loading without restart
- **WHEN** un cliente envia una actualización de sus reglas (.drl) a la API admin
- **THEN** el sistema valida el archivo, almacena la nueva version en DB, recompila solo ese tenant's KieContainer y actualiza el cache sin reiniciar la aplicacion

#### Scenario: Invalid DRL rejected and old version kept active
- **WHEN** un cliente envia un archivo .drl con errores de sintaxis
- **THEN** el sistema retorna un error 400, no almacena la nueva version y mantiene activa la ultima version valida

#### Scenario: Conflict resolution by salience
- **WHEN** dos reglas Drools para el mismo cliente evaluan la misma credencial con severidades diferentes (CRITICO=100 vs ALTO=80)
- **THEN** Drools selecciona la regla con mayor saliencia (la de CRITICO)

#### Scenario: Alert captures the evaluated rule version
- **WHEN** una alerta es evaluada por reglas Drools
- **THEN** el sistema almacena `evaluated_rule_version` en el registro de la alerta para auditabilidad

## RESERVED — Action executor specs live in the `action-executor-credential-rotation` change

---
