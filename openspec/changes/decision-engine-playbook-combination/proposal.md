## Why

El proyecto "Motor de Respuesta a Exposicion de Credenciales" necesita un motor que combine las instrucciones estandarizadas de los playbooks con las reglas especificas declaradas por cada cliente para producir una nivel de criticidad final. Actualmente no existe ning mecanismo para combinar estas dos fuentes de informacion — los playbooks establecen pisos minimos de severidad y las reglas del cliente pueden elevarlos, pero nunca bajarlos.

## What Changes

- Motor que combina `playbook_floor` con `reglas_cliente` mediante la formula `max(playbook_floor, reglas_cliente)` para determinar criticidad final
- Integracion con Drools multi-tenant: cada cliente mantiene sus propios archivos `.drl`, cargados dinamicamente sin reiniciar la aplicacion
- Definicion de playbooks standard en formato YAML/JSON como capa base (peso menor que las reglas del cliente)
- Estrategia de versionado y conflict resolution para reglas Drools activas vs alertas en vuelo

## Capabilities

### New Capabilities
- `decision-engine`: Motor que combina la severidad base de playbooks standard con reglas definidas por el cliente usando una formula de maximo (playbook floor ≤ criticidad final). Incluye soporte multi-tenant con carga dinamica de reglas Drools sin reiniciar la aplicacion.

### Modified Capabilities
- *Ninguna — no existen specs modificadas en `openspec/specs/`.*

## Impact

- Nuevo modulo `decision` en Spring Boot con subcomponentes: decision engine, Drools integration layer (KieContainer/KieFileSystem por cliente)
- Capacitante de reglas Drools por cliente almacenadas en DB o filesystem hot-reloadable sin reinicio
- Dependencia de la capa de auditoria/observabilidad para registrar razonamientos de decision (regla activada, severidad calculada, playbook usado)
- Output del decision engine se conecta al pipeline de acciones del action-executor change con formato: { severity, rationale, playbook_id, calculated_via }
