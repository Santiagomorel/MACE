## 1. Agregar metodo convertToDrl en PlaybookLoaderService

- [ ] 1.1 Crear metodo convertToDrl(Playbook playbook) que recibe un objeto Playbook y retorna string DRL
- [ ] 1.2 Generar package declaration con nombre del playbook (com.security.rules.{playbookId})
- [ ] 1.3 Generar import de Severidad, Alert en el DRL
- [ ] 1.4 Generar una regla Drools por cada entrada en severity_floor del playbook
- [ ] 1.5 Mapear severidad a salience: BAJO=10, MEDIA=20, ALTO=30, CRITICO=40
- [ ] 1.6 Cada regla Drools debe setear alert.setSeverity() segun el mapping del severity_floor
- [ ] 1.7 Cada regla Drools debe incluir condition del playbook (provider, detectionSource)
- [ ] 1.8 Agregar regla collector al final que determina la severidad final con mayor salience

## 2. Tests unitarios de conversion YAML a DRL

- [ ] 2.1 Test: convertToDrl genera DRL valido para aws-access-key-exposed
- [ ] 2.2 Test: convertToDrl genera DRL valido para aws-session-token-leaked
- [ ] 2.3 Test: convertToDrl genera DRL valido para aws-root-credentials-exposed
- [ ] 2.4 Test: convertToDrl genera DRL valido para aws-iam-role-assumption-abuse
- [ ] 2.5 Test: cada regla tiene salience correcto segun severidad (CRITICO=40, etc.)
- [ ] 2.6 Test: el DRL generado pasa validacion de Drools (DroolsRuleService.validateDrl)
- [ ] 2.7 Test: la regla collector existe y tiene logica de maximo

## 3. Integrar DRL de playbooks en DroolsRuleService

- [ ] 3.1 Agregar dependencia de PlaybookLoaderService a DroolsRuleService
- [ ] 3.2 Agregar metodo getPlaybookDrl(String credentialType) que genera DRL del playbook
- [ ] 3.3 Modificar getSession(tenantId) para incluir DRL del playbook junto con DRL del cliente
- [ ] 3.4 Combinar DRL del playbook y DRL del cliente en un unico KieFileSystem
- [ ] 3.5 Agregar regla collector al DRL combinado (si no la incluye el playbook)
- [ ] 3.6 Validar DRL combinado antes de crear KieSession

## 4. Habilitar evaluacion Drools en CriticalityCalculator

- [ ] 4.1 Agregar dependencia de DroolsRuleService a CriticalityCalculator
- [ ] 4.2 Modificar evaluateClientRules() para recibir KieSession y ejecutar reglas
- [ ] 4.3 Crear objeto Alert con tenantId y metadata de la evaluacion
- [ ] 4.4 Insertar Alert en KieSession y fireAllRules()
- [ ] 4.5 Leer alert.getSeverity() como resultado de la evaluacion
- [ ] 4.6 Capturar nombre de reglas disparadas para el rationale

## 5. Actualizar DecisionEngineService

- [ ] 5.1 Modificar evaluate() para pasar actionMatrix y metadata a CriticalityCalculator
- [ ] 5.2 Actualizar rationale para incluir reglas evaluadas (playbook y cliente)
- [ ] 5.3 Asegurar que compliance_tags se extraen del playbookId del resultado

## 6. Eliminar logica Java obsoleta

- [ ] 6.1 Remover metodo determinePlaybookFloor() de CriticalityCalculator
- [ ] 6.2 Remover metodo getHighestPermission() de CriticalityCalculator
- [ ] 6.3 Remover metodo severityForAction() de CriticalityCalculator
- [ ] 6.4 Remover import de SeveridadUtil de CriticalityCalculator
- [ ] 6.5 Remover logica de max() en calculateCriticality()
- [ ] 6.6 Remover campo playbookFloor del CriticalityResult si ya no es necesario

## 7. Tests de integration

- [ ] 7.1 Test: evaluacion con s3_full_access=true devuelve CRITICO (playbook floor)
- [ ] 7.2 Test: evaluacion con iam_modify=true devuelve CRITICO (playbook floor)
- [ ] 7.3 Test: evaluacion con s3_read_only=true devuelve ALTO (playbook floor)
- [ ] 7.4 Test: evaluacion sin permisos activos devuelve BAJO
- [ ] 7.5 Test: reglas del cliente pueden elevar severidad por encima del playbook floor
- [ ] 7.6 Test: reglas del cliente NO pueden bajar por debajo del playbook floor
- [ ] 7.7 Test: evaluacion con condicion detectionSource=webhook activa el playbook
- [ ] 7.8 Test: evaluacion con credentialType=ASIA usa playbook session-token-leaked

## 8. Tests de integration con DroolsRuleService

- [ ] 8.1 Test: KieSession se crea con reglas de playbook + reglas del cliente
- [ ] 8.2 Test: KieSession cacheado con Caffeine y TTL configurable
- [ ] 8.3 Test: reglas del cliente se regeneran al actualizar version en DB
- [ ] 8.4 Test: validacion DRL falla para DRL invalido
- [ ] 8.5 Test: rollback funciona al detectar DRL invalido

## 9. Refactorizacion y cleanup

- [ ] 9.1 Revisar si Playbook.java puede simplificarse (quitar redundancias)
- [ ] 9.2 Agregar logs de diagnostico: que reglas se dispararon, con que salience
- [ ] 9.3 Agregar metrics de Drools (reglas evaluadas, tiempo de ejecucion)
- [ ] 9.4 Documentar formato del rationale con Drools
- [ ] 9.5 Run full test suite y verificar 70% coverage de JaCoCo
