## 1. Playbook Schema Definition + Storage

- [ ] 1.1 Definir clase schema `Playbook` con campos: playbook_id, conditions (provider, credential_type), severity_floor (map acciones→severidad), auto_rotate (enabled, max_window_mins)
- [ ] 1.2 Validar schema YAML/JSON ante carga — todos los campos obligatorios deben existir segun spec
- [ ] 1.3 Crear tabla DB `playbooks` con versionado automatico para archivos de playbooks standard
- [ ] 1.4 Implementar loader de playbooks que parsea YAML a schema Playbook y valida estructura
- [ ] 1.5 Aplicar defaults automáticos cuando playbook carece de sección auto_rotate: enabled=false, max_window_mins=60

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
- [ ] 3.5 Implementar cache LRU por tenant del KieContainer construido con invalidation al detectar cambio en DB
- [ ] 3.6 Agregar TTL de 5 minutos al cache LRU — invalida container automaticamente

## 4. Rule Validation & Rollback

- [ ] 4.1 Verificar que archivos DRL incorrectos retornan error 400 sin almacenar ni activar nueva version
- [ ] 4.2 Mantener ultima version valida activa cuando se rechaza una actualizacion de reglas (rollback automatico)
- [ ] 4.3 Cada alerta almacena `evaluated_rule_version` en su registro para auditabilidad

## 5. Conflict Resolution — Salience-Based Drools Agenda

- [ ] 5.1 Mapear severidades a saliencia: CRITICO=100, ALTO=80, MEDIA=60, BAJO=45
- [ ] 5.2 En caso de empate en saliencia, usar orden cronologico inverso (ultima definicion gana)
- [ ] 5.3 Dos reglas para mismo cliente y misma credencial se resuelven mediante saliencia nativo de Drools

## 6. Alert Versioning & Auditability

- [ ] 6.1 Cuando cambian reglas activas en production, nueva version (client_rules.version += 1)
- [ ] 6.2 Alerta que estaba en vuelo al momento del cambio mantiene su criticidad calculada original — no se recalcula
- [ ] 6.3 Nueva alerta despues del cambio usa las nuevas reglas vigentes

## 7. API Admin Endpoint for Rule Management

- [ ] 7.1 Exponer endpoint POST/PATCH que permite a cliente enviar actualizacion de sus reglas .drl
- [ ] 7.2 Endpoint valida el archivo DRL, lo almacena en DB como nueva version, recompila KieContainer del tenant
- [ ] 7.3 Retorna error 400 con detalle de sintaxis si validacion falla

## 8. Integration — Connect Decision Engine Output to Pipeline

- [ ] 8.1 Integrar decision engine con pipeline de respuesta: recibe { action_matrix, blast_radius, last_used_date } del verifier
- [ ] 8.2 Output del motor: { severity, rationale, playbook_id, calculated_via } conectado al action-executor
- [ ] 8.3 Registrar razonamiento de decision (regla activada, severidad calculada, playbook usado) en capa de observabilidad

## 9. Risks / Performance Mitigations

- [ ] 9.1 Lazy load por tenant para cold start de Drools al compilar .drl en memoria
- [ ] 9.2 Rolling update del KieContainer — nueva version no interrumpe evaluacion de alertas existentes
- [ ] 9.3 Validar que archivos .drl normalmente son < 50 KB — verificar overhead negligible con bytes reales

## 10. Testing and Validation

- [ ] 10.1 Test unitario: formula max() en todos los casos combinados (playbook x reglas_cliente)
- [ ] 10.2 Test unitario: validacion de DRL invalido → rejected, version anterior permanece activa
- [ ] 10.3 Test unitario: conflicto de saliencia entre dos reglas para mismo cliente/credencial
- [ ] 10.4 Test unitario: hot-reload sin reinicio de aplicacion cuando se actualizan reglas
- [ ] 10.5 Test de integracion: pipeline completo — alerta → verdict → decision engine output → action-executor input
- [ ] 10.6 Test end-to-end con KieContainer real (no mock) validando compilacion y carga dinamica
