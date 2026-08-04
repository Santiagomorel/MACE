## Why

El proyecto "Motor de Respuesta a Exposicion de Credenciales" necesita un motor que combine las instrucciones estandarizadas de los playbooks con las reglas especificas declaradas por cada cliente para producir un nivel de criticidad final. Actualmente no existe ningun mecanismo para:

- Combinar estas dos fuentes de informacion — los playbooks establecen pisos minimos de severidad y las reglas del cliente pueden elevarlos, pero nunca bajarlos
- Descubrir automaticamente los permisos reales del cliente desde su cuenta AWS y generar reglas iniciales sin que el cliente tenga que escribir un archivo .drl
- Entregar al cliente una interfaz web para ajustar criticidades sin tocar archivos ni texto de reglas

## What Changes

- Motor que combina `playbook_floor` con `reglas_cliente` mediante la formula `max(playbook_floor, reglas_cliente)` para determinar criticidad final
- Integracion con Drools multi-tenant: cada cliente mantiene sus propios archivos `.drl`, cargados dinamicamente sin reiniciar la aplicacion (KieFileSystem en memoria)
- **Auto-generation de reglas desde metadata AWS:** Servicio que descubre permisos activos del cliente (ListAccessKeys, GetPolicyAttachments, bucket ACLs, IAM roles, EC2) y genera reglas Drools iniciales basadas en los playbooks existentes
- Tres mecanismos de actualizacion de reglas: pull periodico cada 3 horas, boton manual de pull on-demand desde UI, pull instantaneo al detectar exposicion por webhook
- Semaforo dedup con TTL 15min para eliminar regeneraciones paralelas entre pull periodico y webhook-triggered
- Manejo de credenciales expiradas del cliente: notificacion para actualizar creds + estado `PENDING: CRED_REFRESH` cuando discovery falla por acceso denegado
- Definicion de playbooks standard en formato YAML/JSON como capa base (peso menor que las reglas del cliente)
- Estrategia de versionado y conflict resolution para reglas Drools activas vs alertas en vuelo
- UI cliente — el cliente nunca interactua con archivos .drl, solo ve reglas auto-generated con criticidades sugeridas desde una interfaz web

## Capabilities

### New Capabilities
- `decision-engine`: Motor que combina la severidad base de playbooks standard con reglas definidas por el cliente usando una formula de maximo (playbook floor ≤ criticidad final). Incluye soporte multi-tenant con carga dinamica de reglas Drools sin reiniciar la aplicacion.
- `rule-auto-generator`: Servicio que descubre metadata de credenciales AWS del cliente y genera automaticamente un archivo .drl basado en los playbooks estandar, actualizable mediante pull periodico (3h), botón manual, o evento instantaneo al detectar exposicion
- `client-rules-ui`: Interfaz web para que el cliente visualice y ajuste criticidades de reglas auto-generadas sin tocar archivos .drl, con restricciones de playbook floor infranqueable

### Modified Capabilities
- *Ninguna — no existen specs modificadas en `openspec/specs/`.*

## Impact

- Nuevo modulo `decision` en Spring Boot con subcomponentes: decision engine, Drools integration layer (KieFileSystem por cliente), AWS metadata discovery service, rule auto-generator scheduled job, client rules UI endpoint
- AWS metadata discovery utiliza APIs de AWS (ListAccessKeys, GetPolicyAttachments, ListBuckets, ListIAMRoles, DescribeEC2Instances) — scope AWS-only en R1
- Capacitante de reglas Drools por cliente almacenadas en PostgreSQL (BYTEA table) hot-reloadable sin reinicio
- Dependencia de la capa de auditoria/observabilidad para registrar razonamientos de decision (regla activada, severidad calculada, playbook usado)
- Output del decision engine se conecta al pipeline de acciones del action-executor change con formato: { severity, rationale, playbook_id, calculated_via }
