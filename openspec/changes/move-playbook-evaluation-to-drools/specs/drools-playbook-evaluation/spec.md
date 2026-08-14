## ADDED Requirements

### Requirement: Playbook YAML compila a reglas Drools
El sistema SHALL compilar el contenido de cada playbook YAML en reglas Drools (.drl) automaticamente durante el inicio y cada vez que se recargue un playbook.

#### Scenario: Compilation on startup
- **WHEN** el servicio inicia con playbooks en la base de datos
- **THEN** cada playbook YAML se convierte a reglas Drools y se inserta en el KieFileSystem del motor

#### Scenario: Compilation on playbook reload
- **WHEN** un playbook se actualiza en la base de datos
- **THEN** las reglas Drools se regeneran y se recarga la sesion correspondiente

### Requirement: Evaluacion unificada de criticidad
El sistema SHALL evaluar la criticidad de una alerta ejecutando un unico motor Drools que contiene tanto las reglas del playbook como las reglas del cliente.

#### Scenario: Unified evaluation with playbook floor
- **WHEN** una alerta con credencial AKIA llega y el actionMatrix tiene s3_full_access=true
- **THEN** el motor Drools evalua la regla del playbook para s3_full_access y devuelve CRITICO

#### Scenario: Unified evaluation with client rules elevation
- **WHEN** una alerta tiene playbookFloor=MEDIA pero las reglas del cliente detectan un riesgo mayor
- **THEN** el motor Drools devuelve la mayor de las dos severidades

#### Scenario: Unified evaluation with no match
- **WHEN** una alerta no coincide con ningun playbook ni regla de cliente
- **THEN** el motor Drools devuelve BAJO por defecto

### Requirement: Reglas de severidad del playbook como reglas Drools
Cada entrada en `severity_floor` del playbook SHALL convertirse en una regla Drools individual con salience proporcional a su severidad.

#### Scenario: Severity floor becomes Drools rule
- **WHEN** el playbook tiene `severity_floor: { s3_full_access: CRITICO }`
- **THEN** se genera una regla Drools `rule "aws-access-key-exposed_s3_full_access"` con salience=40

#### Scenario: Severity ranking maps to salience
- **WHEN** el sistema procesa diferentes niveles de severidad
- **THEN** BAJO=10, MEDIA=20, ALTO=30, CRITICO=40 se asignan como salience en las reglas Drools

### Requirement: Reglas del cliente comparten motor con reglas del playbook
Las reglas Drools del cliente (generadas por DroolsRuleGenerator) SHALL coexistir en el mismo KieSession que las reglas del playbook.

#### Scenario: Coexistence of playbook and client rules
- **WHEN** un KieSession se crea para un tenant
- **THEN** contiene las reglas del playbook (globales) y las reglas del cliente (dinamicas)

#### Scenario: Playbook rules cannot be overridden downward
- **WHEN** las reglas del cliente y del playbook generan severidades diferentes
- **THEN** la regla con mayor salience (mayor severidad) determina el resultado final

### Requirement: DecisionEngine ejecuta evaluacion Drools
DecisionEngineService.evaluate() SHALL ejecutar el KieSession con el objeto Alert y obtener la criticidad del resultado, eliminando el calculo `max()` en Java.

#### Scenario: Evaluation through Drools session
- **WHEN** DecisionEngineService.evaluate() se invoca con una alerta
- **THEN** se crea un KieSession, se inserta el Alert, se firman todas las reglas y se lee la severidad final

#### Scenario: Rationale includes both sources
- **WHEN** la evaluacion Drools completa
- **THEN** el rationale indica que reglas del playbook y del cliente participaron en la decision

### Requirement: PlaybookLoaderService genera DRL de playbooks
PlaybookLoaderService SHALL tener un metodo que reciba el contenido YAML de un playbook y devuelva el contenido DRL correspondiente.

#### Scenario: YAML to DRL conversion
- **WHEN** PlaybookLoaderService.convertToDrl() recibe el YAML de "aws-access-key-exposed"
- **THEN** devuelve un string DRL con una regla por cada entrada en severity_floor

#### Scenario: DRL includes package with tenant isolation
- **WHEN** el DRL se genera para un playbook
- **THEN** el paquete incluye el nombre del playbook para aislar las reglas
