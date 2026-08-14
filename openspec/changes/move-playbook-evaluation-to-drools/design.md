## Context

Actualmente, MACE calcula la criticidad de alertas de credenciales expuestas combinando dos fuentes en Java:
- **Playbook YAML**: Se lee desde BD, parsea con Jackson YAML, busca en un mapa `severityFloor`
- **Reglas del cliente (Drools)**: Se generan desde metadata de AWS, pero `evaluateClientRules()` retorna `BAJO` fijo

La combinacion se hace con `max(playbookFloor, clientRules)` en `CriticalityCalculator`. El motor Drools existe (DroolsRuleService con KieFileSystem) pero nunca se ejecuta en el flujo de decision.

**Constraint clave**: El proyecto usa Drools 10.2.0 con compilacion in-memory via KieFileSystem. Las reglas del cliente se almacenan como BYTEA en PostgreSQL. Multi-tenant con aislamiento por paquete Drools.

## Goals / Non-Goals

**Goals:**
- Mover toda la logica de decision al motor Drools (playbook + cliente en un unico motor)
- Habilitar la ejecucion real de reglas Drools en CriticalityCalculator
- Generar DRL desde YAML de playbooks en PlaybookLoaderService
- Mantener el `severityFloor` como piso minimo (las reglas del cliente no pueden bajar la severidad)
- Preservar multi-tenant isolation existente

**Non-Goals:**
- Cambiar la estructura de los archivos YAML existentes
- Modificar el esquema de la base de datos
- Agregar nuevas dependencias externas
- Cambiar el API de DecisionEngine (same interface, different implementation)

## Decisions

### Decision 1: Unificar reglas en un unico KieSession por tenant

**Eleccion:** Cada tenant tendra un KieSession que contiene tanto las reglas del playbook como las reglas del cliente.

**Rationale:**
- Una unica ejecucion es mas eficiente que dos evaluaciones separadas
- Drools permite combinar reglas con diferentes saliences en una agenda unica
- Las reglas con mayor salience se ejecutan primero, lo que permite elevar la severidad pero no bajarla

**Alternativas consideradas:**
- Dos KieSessions separados (playbook y cliente) -> Mas complejidad, dos ejecuciones
- Reglas de playbook en Java, reglas de cliente en Drools -> El problema original persiste

### Decision 2: Generar DRL desde YAML en PlaybookLoaderService

**Eleccion:** PlaybookLoaderService tendra un metodo `convertToDrl(Playbook playbook)` que genera el DRL directamente desde el objeto Playbook parseado.

**Rationale:**
- Ya tenemos un objeto Playbook con todos los datos necesarios
- Evita parsear YAML dos veces (una para DRL, otra para el objeto)
- La pipeline queda: YAML -> BD -> Parsear a Playbook -> Generar DRL -> KieFileSystem

**Alternativas consideradas:**
- Generar DRL desde el YAML crudo -> Mas error-prone, perderia validaciones de Playbook
- Usar un servicio dedicado -> Overkill, PlaybookLoaderService ya maneja el ciclo de vida del playbook

### Decision 3: Usar una regla "collector" para determinar criticidad final

**Eleccion:** Las reglas de severity_floor setean `alert.setSeverity()`, y una regla collector al final revisa si hay reglas con mayor severidad (del cliente) y actualiza si es necesario. La regla collector usa `lock-on-active` y `no-loop` para evitar ciclos.

**Rationale:**
- La regla collector garantiza que el resultado final sea el maximo de todas las reglas evaluadas
- `lock-on-active` evita que la regla collector re-dispare las reglas de severity_floor
- Mantiene la semantica existente: playbook floor es minimo, cliente puede elevar

**Alternativas consideradas:**
- Regla collector simple con `insert()` -> Podria causar ciclos sin lock-on-active
- Declarar severidad en facts separados -> Mas complejo, cambia el modelo de datos

### Decision 4: Mantener separacion de paquetes por tenant

**Eleccion:** Las reglas del playbook se generan en el mismo namespace `com.security.rules.{tenantId}`, igual que las reglas del cliente.

**Rationale:**
- Preserva el modelo de multi-tenant existente
- Simplifica la gestion: un KieSession = un tenant = un paquete
- No hay conflicto de nombres porque las reglas tienen nombres unicos

## Risks / Trade-offs

| Risk | Impacto | Mitigacion |
|------|---------|------------|
| Performance: Drools puede ser mas lento que un mapa | Latencia en evaluacion de criticidad | Caching de KieSession por tenant con Caffeine (ya existe) |
| Reglas Drools complejas pueden ser dificiles de debuggear | Mantenibilidad | Log de rationale detallado, incluir nombre de reglas evaluadas en resultado |
| Generacion de DRL puede fallar silenciosamente | Playbooks inválidos en produccion | Validacion DRL antes de insertar en KieFileSystem (ya existe en validateDrl) |
| Break changes: eliminar logica de CriticalityCalculator | Regression en evaluacion actual | Tests de integration que validen misma salida para casos existentes |

## Migration Plan

### Fase 1: Agregar conversor YAML a DRL
- Crear `PlaybookLoaderService.convertToDrl(Playbook)`
- Tests unitarios de conversion para cada playbook existente
- Sin cambio de comportamiento aun

### Fase 2: Habilitar evaluacion Drools
- Modificar `CriticalityCalculator.evaluateClientRules()` para ejecutar KieSession
- Agregar regla collector al DRL generado
- Tests de integration que validen salida identica al comportamiento actual

### Fase 3: Eliminar logica Java de decision
- Remover `determinePlaybookFloor()` y `getHighestPermission()`
- Remover `max()` en CriticalityCalculator
- Remover `severityForAction()` en CriticalityCalculator
- Actualizar tests existentes

### Despliegue
- Deploy sin rollback necesario: el comportamiento es equivalente
- Si hay problemas, revertir los cambios en CriticalityCalculator vuelve al comportamiento anterior

### Rollback
- Si Drools falla, usar fallback: ejecutar la logica Java actual
- Implementar via un flag de feature toggle en application.yml

## Open Questions

1. **Salience de la regla collector**: Deberia ser menor que la maxima severidad (ej: 5) para que se ejecute despues de todas las reglas de severity? O deberia tener la maxima para asegurar que se ejecuta al final?

2. **Rationale detallado**: El rationale actual es `"max(playbook_floor=X, client_rules=Y)"`. Con Drools, el rationale deberia listar las reglas evaluadas y sus resultados individuales. Hay que decidir el formato.

3. **Playbooks con condiciones complejas**: El YAML actual tiene `conditions.provider` y `conditions.detectionSource`. Estas condiciones se incorporan a las reglas Drools generadas (ej: `when Alert(detectionSource == "webhook")`)?

4. **Cuantos KieSessions**: Con Caffeine cacheando por tenant, se crea un KieSession por tenant. Si hay muchos tenants con playbooks distintos, puede haber muchos KieSessions. Hay que definir un limite maximo razonable (actualmente max 100).
