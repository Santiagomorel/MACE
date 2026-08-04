## Why

El proyecto "Motor de Respuesta a Exposicion de Credenciales" necesita un componente central que verifique si las credenciales expuestas detectadas por GitGuardian son validas y determina su nivel de acceso en la infraestructura cloud del cliente. Actualmente no existe ning mecanismo para corroborar el estado real de un secreto expuesto ni calcular su potencial impacto sobre los servicios del cliente, lo cual impide la toma de decisiones automatizada posterior.

## What Changes

- Motor que recibe alertas normalizadas de GitGuardian y verifica credenciales individuales contra la infraestructura cloud del cliente
- Deteccion automatica del tipo de proveedor (AWS, Azure) mediante heuristica por prefix como fallback si GitGuardian no lo incluye
- Búsqueda del account del cliente correspondiente a una credencial expuesta cuando no hay hint confiable disponible
- Enumeracion de permisos efectivos (aproximados) para cada identity valida usando las credenciales de admin de solo lectura del cliente
- Construccion de un action-permission matrix como output principal para alimentar el motor de evaluacion de riesgo con criterios Drools, evitando enumeracion a nivel de recursos ARN por ahora

## Capabilities

### New Capabilities
- `credential-verifier`: Capacidad central que ingresa alertas normalizadas de GitGuardian, verifica la validez de credenciales individuales contra infraestructura cloud via APIs directas del cliente, enumera permisos efectivos (ALLOW - DENY) y produce un action-permission matrix como output

### Modified Capabilities
- *Ninguna — este es el primer cambio en el proyecto*

## Impact

- Nuevo modulo `verification` en el backend Spring Boot con subcomponentes: provider detectors (AWS, Azure, GCP), permission enumerator, account mapper y cache layer con circuit breakers
- Dependencia de credenciales de admin de solo lectura por cliente almacenadas de forma segura (vault o DB encriptada)
- Output del verificador integra directly con el modulo de reglas Drools via `action_matrix`, `blast_radius` estimado y `last_used_date` para activar la evaluacion de riesgo
