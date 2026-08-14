## Why

El motor de decision calcula la criticidad de una alerta combinando dos fuentes: el playbook (YAML, evaluado en Java) y las reglas del cliente (Drools, nunca ejecutadas). El `max()` se hace en codigo Java, lo que limita la logica a una sola operacion. Esto impide reglas complejas como: "si el cliente tiene s3_full_access Y la alerta viene de un canal no confiable, subir a CRITICO".

## What Changes

- Mover la evaluacion del playbook de Java a reglas Drools
- El playbook YAML se compila a reglas Drools (.drl) en tiempo de inicio y/o reload
- CriticalityCalculator ejecuta el motor Drools con AMBAS fuentes (playbook + reglas cliente) en un unico motor
- Eliminar el `max()` en Java: Drools decide la criticidad final con una unica regla que combina ambas
- Habilitar `evaluateClientRules()` para ejecutar reglas Drools reales
- Soporte para logica compleja: condiciones compuestas, correlacion entre fuentes, prioridades configurables

## Capabilities

### New Capabilities

- `drools-playbook-evaluation`: Compilacion de playbooks YAML a reglas Drools y evaluacion unificada de criticidad en el motor Drools

### Modified Capabilities

<!-- No existing specs to modify -->

## Impact

- `CriticalityCalculator.java` - reemplazar logica `max()` por ejecucion Drools
- `PlaybookLoaderService.java` - agregar compilacion de YAML a DRL
- `DecisionEngineService.java` - actualizar flujo de evaluacion
- `DroolsRuleService.java` - extender para manejar reglas combinadas (playbook + cliente)
- `Playbook.java` - estructura de datos puede simplificarse
- `DroolsRuleGenerator.java` - extender para incluir reglas de playbook
- Dependencias: ninguna nueva dependencia externa
