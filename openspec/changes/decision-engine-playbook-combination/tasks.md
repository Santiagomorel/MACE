## 1. Playbook Schema Definition + Storage

- [ ] 1.1 Definir clase schema `Playbook` con campos: playbook_id, conditions (provider, credential_type), severity_floor (map acciones→severidad), auto_rotate (enabled, max_window_mins)
- [ ] 1.2 Validar schema YAML/JSON ante carga — todos los campos obligatorios deben existir segun spec
- [ ] 1.3 Crear tabla DB `playbooks` con versionado automatico para archivos de playbooks standard
- [ ] 1.4 Implementar loader de playbooks que parsea YAML a schema Playbook y valida estructura
- [ ] 1.5 Aplicar defaults autom\u00e1ticos cuando playbook carece de sesi\u00f3n auto_rotate: enabled=false, max_window_mins=60
- [ ] 1.6 Definir tabla `credentials` con campos: id, tenant_id, credential_type (access_key / session_token), provider_arn, status (ACTIVE/INACTIVE)

## 2. Severity Calculation Engine — Formula Computation

- [ ] 2.1 Implementar funcion `calculateCriticality(playbook_floor, reglas_cliente)` aplicando formula max()
- [ ] 2.2 Definir enum Severidad total_order: BAJO=1, MEDIA=2, ALTO=3, CRITICO=4
- [ ] 2.3 Validar que playbook_floor nunca es superado hacia abajo — solo puede ser igual o mayor por reglas del cliente
- [ ] 2.4 Test unitario: playbook_floor ALTO, reglas_cliente CRITICO → resultado CRITICO
- [ ] 2.5 Test unitario: playbook_floor CRITICO, reglas_cliente BAJO → resultado CRITICO (floor se mantiene)
- [ ] 2.6 Test unitario: playbook_floor igual a reglas_cliente → resultado igual
- [ ] 2.7 Test unitario: severe action elevates criticality (playbook_floor MEDIA + s3_write+ec2_modify → CRITICO)

## 3. Drools Integration Layer — KieFileSystem in-Memory Hot-Reload

- [ ] 3.1 Implementar `DroolsRuleService` con KieFileSystem en memoria para carga dinamica de reglas (.drl) por cliente
- [ ] 3.2 Implementar validacion de reglas con `KieContainer.validate()` antes de activar nueva version
- [ ] 3.3 Crear tabla PostgreSQL `client_rules` con campos: id, tenant_id, version, drl_content[BYTEA], created_at
- [ ] 3.4 Implementar auto-incremento de version por cliente cuando reglas se actualizan
- [ ] 3.5 Implementar cache LRU por tenant del KieContainer construido con invalidation al detectar cambio en DB (TTL 5 min)
- [ ] 3.6 Validar que .drl para un cliente es < 50 KB typicalmente

## 4. Rule Validation & Rollback

- [ ] 4.1 Verificar que archivos DRL incorrectos retornan error 400 sin almacenar ni activar nueva version
- [ ] 4.2 Mantener ultima version valida activa cuando se rechaza una actualizacion de reglas (rollback automatico)
- [ ] 4.3 Cada alerta almacena `evaluated_rule_version` en su registro para auditabilidad

## 5. Conflict Resolution — Priority-Based Drools Agenda

- [ ] 5.1 Mapear severidades a saliencia: CRITICO=100, ALTO=80, MEDIA=60, BAJO=40
- [ ] 5.2 En caso de empate en saliencia, usar orden cronologico inverso (ultima definicion gana)
- [ ] 5.3 Dos reglas para mismo cliente y misma credencial se resuelven mediante saliencia nativo de Drools

## 6. Alert Versioning & Auditability

- [ ] 6.1 Cuando cambian reglas activas en production, nueva version (client_rules.version += 1)
- [ ] 6.2 Alerta que estaba en vuelo al momento del cambio mantiene su criticidad calculada original — no se recalcula
- [ ] 6.3 Nueva alerta despues del cambio usa las nuevas reglas vigentes

## 7. AWS Metadata Discovery Service (Decision 3)

- [ ] 7.1 Implementar servicio `AwsMetadataDiscovery` que consulta APIs de AWS para cada cliente con credenciales read-only admin:
  - ListAccessKeys() → keys activas + lastUsed date
  - GetPolicyAttachments() → policies asociadas al key
  - GetBucketACLs() → buckets accesibles y permisos
  - ListIAMRoles/Policies() → roles y permisos vinculados
  - DescribeEC2Instances() → instancias en cuenta
- [ ] 7.2 Implementar mapeo automatico de permisos detectados a severidades base (playbook_floor): s3_full_access → CRITICO, iam_modify → CRITICO, ec2_instance_control → CRITICO, nothing_active → BAJO
- [ ] 7.3 Generar .drl en memoria basado en metadata descubierta + mapeo de severidades → guardarlo en DB `client_rules` → invalidar cache LRU del KieContainer del tenant
- [ ] 7.4 Manejar credenciales expiradas: si las AWS creds de lectura del cliente caducan y el discovery falla (AccessDenied/AWSSTSExpired) → no regenerar reglas, estado queda `PENDING: CRED_REFRESH`, notificar al cliente

## 8. Rules Update Mechanisms (Decision 3 continued)

- [ ] 8.1 Implementar pull periodico cada 3 horas (scheduled job): descubre cambios en recursos del cliente y regenera reglas si detecta diferencias con la version actual
- [ ] 8.2 Implementar push instantaneo por webhook: cuando el sistema de credenciales expuestas recibe un evento de exposicion, dispara pull inmediato solo para ese recurso afectado
- [ ] 8.3 Implementar semaforo dedup: semaphore (`rule_generation_lock`) con TTL 15 min — si otro proceso ya posee el semaphore durante los primeros 30s del intervalo → salto y espera al proximo ciclo

## 9. Client Rules UI (Decision 4)

- [ ] 9.1 Crear endpoint de reglas por cliente: GET {tenant_id}/rules devuelve reglas auto-generated con criticidades sugeridas
- [ ] 9.2 Permitir a cliente subir cualquier nivel de criticidad sin validacion adicional (ej: subir MEDIA → ALTO o CRITICO)
- [ ] 9.3 Permitir bajar levels de criticidad solo si no queda debajo del playbook floor correspondiente
- [ ] 9.4 Registrar manual_override_by_client = true con timestamp y usuario para cada modificacion
- [ ] 9.5 Cliente NUNCA toca archivos .drl — el sistema traduce los ajustes de UI a .drl internamente y recarga KieContainer

## 10. Integration — Connect Decision Engine Output to Pipeline

- [ ] 10.1 Integrar decision engine con pipeline de respuesta: recibe { action_matrix, blast_radius, last_used_date } del verifier
- [ ] 10.2 Output del motor: { severity, rationale, playbook_id, calculated_via } conectado al action-executor
- [ ] 10.3 Registrar razonamiento de decision (regla activada, severidad calculada, playbook usado) en capa de observabilidad

## 11. Risks / Performance Mitigations

- [ ] 11.1 Lazy load por tenant para cold start de Drools al compilar .drl en memoria
- [ ] 11.2 Rolling update del KieContainer — nueva version no interrumpe evaluacion de alertas existentes
- [ ] 11.3 Validar que archivos .drl normalmente son < 50 KB — verificar overhead negligible con bytes reales

## 12. Testing and Validation

- [ ] 12.1 Test unitario: formula max() en todos los casos combinados (playbook x reglas_cliente)
- [ ] 12.2 Test unitario: validacion de DRL invalido → rejected, version anterior permanece activa
- [ ] 12.3 Test unitario: conflicto de saliencia entre dos reglas para mismo cliente/credencial
- [ ] 12.4 Test unitario: hot-reload sin reinicio de aplicacion cuando se actualizan reglas
- [ ] 12.5 Test de integracion: pipeline completo — alerta → verdict → decision engine output → action-executor input
- [ ] 12.6 Test end-to-end con KieContainer real (no mock) validando compilacion y carga dinámica

## 13. Drools Rule Auto-Generation Service (Decision 7)

- [ ] 13.1 Implementar `DroolsRuleGenerator` que toma un Playbook YAML + metadata descubierta -> genera archivo .drl en memoria:
  ```
  package com.security.rules.<tenant_id>;
  // This file was auto-generated from playbook <playbook_id> (version X.Y.Z) on <timestamp>
  // compliance: <compliance_tags source + control_description joined with "> - " joining>
  import io.security.domain.Alert;
  import io.security.domain.CredentialMetadata;
  ...
  rule "<tenant_id>_severity_level"
    salience <mapped_salience>  /* CRITICO=100, ALTO=80, MEDIA=60, BAJO=40 */
    no-loop true
    lock-on-active true
    agenda-group "rules_<tenant_id>"
    when
      alert: Alert( tenantId == "<tenant_id>", credential.type == "<AKIA|ASIA|root_>" )
      ... (conditions for each permission detected - e.g., s3_full_access)
    then
      alert.severity = Severity.<LEVEL>; /* <mapped_severidad> */
      alert.playbookId = "<playbook_id>";
      insert(alert);
  end
  ```
- [ ] 13.2 Generar una regla por permutation de detected permisos del cliente, combinando con los severity_floor categories del playbook (s3_full_access -> CRITICO, iam_modify -> CRITICO, etc.)
- [ ] 13.3 Insertar `compliance_tags` como comentarios al inicio del .drl:
  ```java
  // compliance: ISO 27001-A.9.4.1 - Management of information systems addresses and directory levels > ISO 27001-A.9.2.2 - Registration of user access
  ```
- [ ] 13.4 Validar generacion produce .drl de menos de 50 KB para un cliente typico (max reglas = permisos_detectados * credential_types)
- [ ] 13.5 Generar agenda-group `"rules_<tenant_id>"` por tenant - drools activa este grupo via `kieSession.getAgenda().getAgendaGroup("rules_<tenant_id>").setFocus();`
- [ ] 13.6 Incluir `no-loop true` y `lock-on-active true` para evitar ejecucion infinita de reglas Drools (practica Drools estandar)

## 14. AWS Metadata Discovery Pipeline Integration

- [ ] 14.1 Implementar `AwsMetadataDiscoveryService` con metodo `discover(tenantId)` que:
  - Recarga credenciales del cliente desde el secrets store
  - Llama a `ListAccessKeys()` -> extrae list de keys activas + `LastUsedDate`
  - Para cada key activa, llame `GetPolicyAttachments()` -> extraje attached IAM policies (s3_full_access, iam_modify, ec2_instance_control, etc.) como `permission_categories`
  - Llama a `ListBucketAccessControlLists()` (o `ListBucketPolicies()`) para buckets S3 activos del tenant
  - Llama a `DescribeEC2Instances(Filters=[...]) -> count de instancias EC2 en la cuenta del cliente
- [ ] 14.2 Mapear permisos descubiertos a severity_floor categories del playbook:
  | Permiso AWS API retorno | Category key -> severity_floor lookup | Nota |
  |---|---|---|
  | `s3_full_access` | `CRITICO` | cualquier bucket writable = riesgo de data exfiltration |
  | `iam_modify` | `CRITICO` | IAM modify permite crear/rotar claves a discrecion del atacante |
  | `ec2_instance_control` | `CRITICO` | EC2 full access puede usarse como pivoting host |
  | `ec2_read_only` | `MEDIA` | lectura limitada no da control de recursos |
  | `s3_read_only` | `ALTO` | lectura de datos sensibles posible pero mas baja que write/modify |
  | `nothing_active` | `BAJO` | key activa pero sin permisos o lastUsed > 90 days ago |
- [ ] 14.3 Al obtener resultados: invocar `DroolsRuleGenerator.generate(playbook, discoveredPermissions)` -> guardar resultado en tabla `client_rules` como nueva version (auto-incremento) -> invalidar cache LRU del KieContainer de ese tenant (trigger DB insert/rollback de triggers o polling por TTL expiration 5 minutos)
- [ ] 14.4 Manejar caso de credentials expiradas: si AWS API retorna `AccessDenied` OR `AWSSTSExpired`:
  - No generar reglas - estado queda `PENDING: CRED_REFRESH`
  - Disparar notificacion al cliente para refrescar credenciales de lectura
  - Mantener reglas anteriores hasta que las nuevas sean refreshed y descubiertas con exito
- [ ] 14.5 Implementar "safe mode" fallback cuando discovery falla en production sin error explicito: mantener ultima version discoverada + alertas `PENDING: RECON`
