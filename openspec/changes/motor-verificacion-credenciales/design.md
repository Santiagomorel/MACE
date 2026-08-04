## Context

El sistema "Motor de Respuesta a Exposicion de Credenciales" requiere un componente central que verifique si las credenciales expuestas detectadas por GitGuardian son validas y determina su nivel de acceso en la infraestructura cloud del cliente. Actualmente no existe ning mecanismo para corroborar el estado real de un secreto expuesto ni calcular su potencial impacto sobre los servicios del cliente, lo cual impide la toma de decisiones automatizada posterior.

El motor debe operar en un escenario donde inicialmente se recibe una alerta unica (no batch masivo), verificar credenciales individualmente y producir un output minimo pero suficiente para alimentar el motor de evaluacion de riesgo Drools.

**Constraints:**
- Fase 1: unicamente AWS como proveedor cloud
- Release 1 target: 27/08/2025
- YAGNI aplicado — solo lo que Drools necesita para evaluar criticidad
- Arquitectura debe soportar escalado concurrente multi-proveedor en fases futuras

## Goals / Non-Goals

**Goals:**
- Verificacion de credenciales expuestas contra infraestructura cloud del cliente via APIs directas
- Deteccion automatica del tipo de proveedor (AWS, Azure) con heuristica por prefix como fallback
- Mapeo de credencial al account del cliente correspondiente
- Enumeracion de permisos efectivos aproximados (ALLOW - DENY por-action)
- Construccion de action-permission matrix para el motor de evaluacion de riesgo
- Cache vertical del resultado de verificacion con TTL configurable
- Circuit breakers por proveedor para evitar cascadas de fallo

**Non-Goals:**
- Enumeracion a nivel de recursos ARN (no se requiere en Release 1)
- Uso del IAM Policy Simulator API ni evaluacion completa de effective permissions
- Soporte completo multi-proveedor (Azure/GCP) — deferred a proxima etapa
- Almacenamiento directo de credenciales de administracion en codigo o repositorio

## Decisions

### Decision 1: Fuente Primaria de Datos
Los datos de inicio provienen exclusivamente de GitGuardian como fuente primaria de alertas. GitGuardian identifica el tipo de proveedor (AWS, Azure, GCP) en sus respuestas y puede incluir account_hint cuando las keys ya son conocidas publicamente.

**Fallback:** Deteccion heuristica por prefix de la credencial:
- AWS: `AKIAxxxxxxxxxx` → AWS
- Azure: `eyJxxxxxxxxxx` (JWT base64) → Azure AD
- GCP: `AIzaSyxxxxxxxxxx` → GCP

### Decision 2: Architecture Choice B — Fan-out Concurrente con Circuit Breakers + Cache
Se selecciona la opcion B para la arquitectura del verificador: fan-out concurrente hacia los providers del cliente, circuito abierto por proveedor cuando hay fallos, cache vertical de resultado por credencial.

**Rationale:** Permite paralelismo eficiente para la verificacion en el caso que surja multi-proveedor sin sacrificar resilience. Evita cascadas de fallo que bloquearian la respuesta completa si un provider esta lento o inaccesible.

### Decision 3: Nivel de Blast Radius = Action Matrix (Nivel 2)
El output minimo del verificador es: `{ account_id, identity_arn, action-matrix: Set<String>, last_used_date }`

**Rationale:** Solo se necesita lo que Drools requiere para evaluar criticidad. Si en el futuro se requiere especificidad a nivel de recursos, se agrega como mejora — YAGNI aplicado.

### Decision 4: Authentication con Credenciales Admin-Read-Only del Cliente
No se usan las credenciales expuestas para verificarse. Se utilizan credenciales de acceso de lectura (admin de solo lectura) del cliente ya existentes en infraestructura.

**Rationale:** Las credenciales expuestas ya no son confiables. Las creds admin-read-only proporcionan el nivel de permiso necesario para verificar estado y enumerar permisos sin riesgo de escritura.

### Decision 5: Account Mapping — Hint-Based con Fallback Iterativo
- Si GitGuardian proporciona account_hint exacto (keys ya conocidas publicamente): se usa directo
- Si no hay hint confiable: se busca iterando accounts del cliente en su infraestructura (Opcion A seleccionada)
- Accounts que no son mapeados se marcan como "UNKNOWN" para investigacion posterior

### Decision 6: Strategy de Enumeracion de Permisos AWS
Pipeline de enumeracion en fases:
1. **GetCallerIdentity** → valida identity y obtiene ARN
2. **ListAttachedUserPolicies + GetUserPolicy** (inline) → collect all policies
3. **Parsear todas las Statements como JSON**: Acumular Actions con Effect = Allow, restar Actions con Effect = Deny (precedence sobre Allow)
4. **Construir action_matrix** con la diferencia ALLOW - DENY

### Decision 7: Permisos Efectivos Aproximados
Se computan permisos aproximados via IAM policies del usuario, NO se usa IAM Policy Simulator API ni evaluacion completa de effective permissions.

**Justificacion:** Demasiado complejo y costoso para Release 1. Los permisos aproximados son suficientes para las reglas Drools.

### Decision 8: Rate Limit Management
Se aplica retry con backoff exponencial para rate limits.

**YAGNI:** Si surge el problema en los rate limits como escenario real, se agrega estrategia adicional (polling distribuido) en ese momento, no antes.

## Risks / Trade-offs

|Risk|Impact|Mitigation|
|---|---|---|
|Credenciales admin del cliente expuestas|Alto|Almacenamiento seguro via vault/DB encriptada; acceso minimo de solo lectura|
|Rate limits de APIs cloud durante enumeration|Medio|Retry con backoff exponencial|
|Cuenta UNKNOWN despues de mapeo intentado|Bajo|Marcar para investigacion manual del equipo|
|Permisos aproximados ≠ permisos reales en algunos casos edge|Bajo|Suficiente para Drools; precision perfecta no requerida en R1|
|Cache TTL 5 min puede mostrar status outdated|Medio|Suficiente dado que creds raramente cambian en ese ventana; se re-valida en nueva alerta|
|Circuit breaker falso positivo (trip) si provider tiene latencia|Medio|Tuning de thresholds; monitoreo post-release|

## Migration Plan

Este es el primer componente del sistema, no hay migracion. Se implementa como nuevo modulo `verification` en el backend Spring Boot integrado con existing pipeline de build/deploy.

**Deployment estrategia:**
1. Desplegar modulo verification como parte del servicio existente
2. Integrar con webhook handler de GitGuardian (existing/integrador-pending)
3. Validar output format contra spec de Drools engine
4. Monitorear circuit breaker trips y cache efficiency en produccion inicial

## Open Questions

1. **Autenticacion con el cliente**: Como se obtienen las credenciales de lectura del admin? (panel API, vault, o configuracion manual?)
2. **Cantidad de accounts por cliente**: Evaluar cuando se detecte el problema real en produccion. No hay suficiente info para definir limite hoy.
3. **Azure/GCP provider support**: Deferred. Se implementara como siguiente etapa de verification providers una vez AWS este estable.

## Dependencies

- **Modulo Drools** (evaluacion de riesgo): output del verificador alimenta criteria de criticidad via `action_matrix`, `blast_radius` estimado, `last_used_date`
- **Integrador de Alertas**: ingesta webhook/GitGuardian alertas normalizadas
- **Vault/Secret Store externo**: storage de credenciales admin-read-only por cliente
