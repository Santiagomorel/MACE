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
El sistema SHALL definir playbooks estandar como archivos estructurados que mapean combinaciones de tipos de credential y permisos a niveles minimos de severidad. El formato base es YAML/JSON con los siguientes campos obligatorios: `playbook_id`, `conditions` (provider, credential_type), `severity_floor` (mapping de acciones a severidades), `auto_rotate.enabled`, `auto_rotate.max_window_mins`.

#### Scenario: Playbook defines severity for AWS access key
- **WHEN** un playbook para "aws-access-key-exposed" se carga con condiciones `provider=aws` y `credential_type=access_key`
- **THEN** el motor asigna automáticamente los pisos de severidad del playbook por cada categoria de permiso (s3_full_access → CRITICO, s3_read_only → ALTO, etc.)

#### Scenario: Playbook without auto_rotate configuration uses defaults
- **WHEN** un playbook carece de la seccion `auto_rotate`
- **THEN** el motor aplica los valores por defecto: `enabled=false` y `max_window_mins=60`

### Requirement: Drools multi-tenant dynamic loading
El sistema SHALL cargar reglas Drools (.drl) dinamicamente por cliente sin reiniciar la aplicacion. Un archivo .drl se valida con `KieContainer.validate()` antes de activar, y se almacena en PostgreSQL con versionado (`client_rules` table with auto-incrementing version). Se cachea el KieContainer construido por tenant con invalidation al detectar cambio en DB. Cada regla incluye un campo `salience` determinado por su severidad (CRITICO=100, ALTO=80, MEDIA=60, BAJO=45).

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
