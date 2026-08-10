# Arquitectura Spring Boot — Estado Actual y Decisiones Pendientes

**Fecha:** 2026-08-09  
**Estado:** Exploración en curso — sin implementacion

---

## Estado Actual

Los temas 1-5 del `nextSesion.txt` ya tienen disenos detallados en OpenSpec, pero **ninguno tiene codigo implementado todavia**. Las decisiones tecnicas estan dispersas en 4 cambios:

```
                    ┌──────────────────────────────┐
                    │   Cambios planificados       │
                    │   (sin implementacion)       │
                    └──────────────┬───────────────┘
                                     │
           ┌─────────────────────────┼─────────────────────────┐
           │                         │                         │
     ┌─────┴─────┐          ┌────────┴────────┐        ┌──────┴──────┐
     │ alert-    │          │ decision-       │        │ action-     │
     │ integrator│          │ engine          │        │ executor    │
     │           │          │ + Drools        │        │             │
     └─────┬─────┘          └────────┬────────┘        └──────┬──────┘
           │                         │                         │
     webhook + dedup         playbook + reglas        state machine
     worker pool             severity calc            rotation flow
     GitGuardian adapter     Drools hot-reload        notifications
           │                         │                         │
           └──────────┬──────────────┼─────────────────────────┘
                      │              │
              ┌───────┴──────────────┴───────┐
              │   logging-infrastructure     │
              │   (transversal a todos)      │
              └──────────────────────────────┘
```

Cada cambio tiene sus propias decisiones tecnicas, pero **no hay un diseño de arquitectura unificada** que responda como se organiza todo junto.

---

## Decisiones YA tomadas (disparso en los designs)

| Decision | Valor | Fuente |
|---|---|---|
| DB principal | PostgreSQL 16 | logging-infrastructure |
| Storage logs | PostgreSQL JSONB (tabla audit_events) | logging-infrastructure |
| Cache | Caffeine (local, sin Redis en Phase 1) | alert-integrator |
| Logging | Logback JSON + MDC + secret redaction | logging-infrastructure |
| Metrics | Micrometer + Actuator | logging-infrastructure |
| Secret storage | "Vault" (sin especificar cual) | action-executor |
| Queue processing | Worker pool inline (5 workers, configurable) | alert-integrator |
| Rules engine | Drools por tenant (KieContainer) | decision-engine |
| Webhook endpoint | POST /api/alerts | alert-integrator |
| Dedup | Event (5min TTL) + Secret (cooldown variable) | alert-integrator |
| DLQ | Tabla DB (alert_dlq) | alert-integrator |
| State machine | PENDING → ROTATING → SUCCESS | FAIL | ESCALATE | action-executor |
| Rotation timeout | 5 minutos global | action-executor |
| Notification | Strategy pattern (Slack, Email, Ticket, SNS) | action-executor |
| Retention logs | 30 dias (auto-purge) | logging-infrastructure |
| Build tool | Maven 3.9.x | BLOQUE A |
| Java | 21 LTS | BLOQUE A |
| Spring Boot | 3.3.x | BLOQUE A |
| PostgreSQL | 16 | BLOQUE A |

---

## Decisiones BLOQUE A — ADOPTADAS (Build Tool + Versions)

**Build Tool: Maven 3.9.x**
- Drools tiene plugin oficial (`kie-maven-plugin`), ejemplos oficiales solo en Maven
- Spring Boot parent POM = zero config para jar
- XML explicito = mas debuggable

**Java: 21 (LTS)**
- Virtual threads para I/O bound (webhooks, AWS calls, DB queries)
- LTS hasta 2031+, pattern matching, records, sealed classes
- Tomcat 10.1 con soporte nativo de virtual threads

**Spring Boot: 3.3.x**
- LTS estable, soporte hasta 2029+
- Maduro para Phase 1, optimizado para Java 21

**PostgreSQL: 16**
- JSONB + GIN indexes para audit_events
- Ampliamente disponible en free tiers (Railway, Supabase, Neon)

**POM parent:** `org.springframework.boot:spring-boot-starter-parent:3.3.x`
**Source/Target:** 21

### Por que estas versiones

```
┌──────────────────────────────────────────────────────────────────┐
│  Maven                                                          │
│  • kie-maven-plugin oficial para .drl compilation              │
│  • Spring Boot parent POM = zero config jar                    │
│  • Kie Maven repository integrado                               │
├──────────────────────────────────────────────────────────────────┤
│  Java 21                                                        │
│  • Virtual threads → miles de webhooks sin ThreadPool manual   │
│  • Record patterns, sequenced collections, sealed classes      │
├──────────────────────────────────────────────────────────────────┤
│  Spring Boot 3.3                                                │
│  • Tomcat 10.1 + virtual threads                               │
│  • Spring Data JPA con PostgreSQL 16+                          │
│  • Actuator health + metrics maduros                           │
├──────────────────────────────────────────────────────────────────┤
│  PostgreSQL 16                                                  │
│  • JSONB + GIN indexes para audit_events                       │
│  • Stable, free tier friendly                                  │
└──────────────────────────────────────────────────────────────────┘
```

### Dependencies principales

```xml
<dependencies>
    <!-- Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>

    <!-- Database -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
    </dependency>

    <!-- Drools -->
    <dependency>
        <groupId>org.drools</groupId>
        <artifactId>drools-core</artifactId>
    </dependency>
    <dependency>
        <groupId>org.drools</groupId>
        <artifactId>drools-compiler</artifactId>
    </dependency>
    <dependency>
        <groupId>org.kie</groupId>
        <artifactId>kie-api</artifactId>
    </dependency>

    <!-- AWS SDK v2 -->
    <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>sts</artifactId>
    </dependency>
    <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>iam</artifactId>
    </dependency>

    <!-- Observability -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-registry-prometheus</artifactId>
    </dependency>

    <!-- Testing -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

---

## Decisiones BLOQUE B — ADOPTADAS (Project Structure)

**Estructura: Maven Multi-Module**

Se adopta un proyecto Maven multi-module con un parent POM que centraliza versionado y dependency management. Cada modulo corresponde a un dominio funcional autonomo con su propio ciclo de vida de build y test.

### Estructura de Modulos

```
openstack-key-rotation/                 (parent POM — no code)
├── pom.xml                             (dependencyManagement, modules)
├── shared/
│   ├── pom.xml
│   ├── shared-models/                  (modelos de dominio compartidos)
│   │   └── src/main/java/com/.../models/
│   │       ├── Alert.java
│   │       ├── VerificationResult.java
│   │       ├── DecisionOutput.java
│   │       ├── RotationState.java
│   │       └── ...
│   └── shared-spi/                     (interfaces — contract layer)
│       └── src/main/java/com/.../spi/
│           ├── AlertAdapter.java
│           ├── VerificationProvider.java
│           ├── DecisionEvaluator.java
│           ├── ActionExecutorService.java
│           └── NotificationChannel.java
├── alert-integrator/                   (ingesta de alerts)
│   └── src/main/java/com/.../
├── verification-engine/                (verificacion de credenciales)
│   └── src/main/java/com/.../
├── decision-engine/                    (motor de reglas Drools)
│   └── src/main/java/com/.../
└── action-executor/                    (ejecucion de rotaciones)
    └── src/main/java/com/.../
```

### DAG de Dependencias

```
                    alert-integrator
                          │
               verification-engine
                          │
                  decision-engine
                          │
                 action-executor
                          │
                       shared/
                  (models + SPIs)
```

**Reglas de dependencias:**

| Modulo | Dependencias directas |
|---|---|
| `shared/shared-models` | Ninguna (libre) |
| `shared/shared-spi` | `shared/shared-models` |
| `alert-integrator` | `shared/shared-models`, `shared/shared-spi` |
| `verification-engine` | `shared/shared-models`, `shared/shared-spi`, `alert-integrator` |
| `decision-engine` | `shared/shared-models`, `shared/shared-spi`, `verification-engine` |
| `action-executor` | `shared/shared-models`, `shared/shared-spi`, `decision-engine` |

**Ningun modulo depende hacia arriba en el DAG.** Esto previene ciclos de compilacion.

### Contract Layer — `shared/spi/`

Las interfaces en `shared/spi/` son el unico mecanismo de comunicacion entre modulos. Esto garantiza:

- **Acoplamiento minimo:** los modulos solo dependen de contratos, no de implementaciones concretas.
- **Cambio seguro:** una implementacion puede evolucionar sin afectar a otros modulos mientras la interface no cambie.
- **Testabilidad:** los modulos pueden testearse con mocks de las interfaces.

#### Versionado de Interfaces (SemVer)

Las interfaces en `shared/spi/` se versionan con semantic versioning:

- `1.0.0` — Primera version estable
- `1.1.0` — Metodos nuevos adicionados (backward compatible)
- `2.0.0` — Breaking changes (metodos removidos, signatures cambiadas)

Solo se introduce un breaking change en una interface si el valor del cambio justifica el costo de actualizacion en todos los consumidores.

#### Ejemplos de Interfaces

**VerificationProvider** (`shared/spi/verification/VerificationProvider.java`)

```java
package com.company.rotations.spi.verification;

import com.company.rotations.models.Alert;
import com.company.rotations.models.VerificationResult;

public interface VerificationProvider {
    VerificationResult verify(Alert alert, String credentialType);
}
```

**DecisionEvaluator** (`shared/spi/decision/DecisionEvaluator.java`)

```java
package com.company.rotations.spi.decision;

import com.company.rotations.models.Alert;
import com.company.rotations.models.VerificationResult;
import com.company.rotations.models.DecisionOutput;

public interface DecisionEvaluator {
    DecisionOutput evaluate(Alert alert, VerificationResult verificationResult);
}
```

**ActionExecutorService** (`shared/spi/action/ActionExecutorService.java`)

```java
package com.company.rotations.spi.action;

import com.company.rotations.models.Alert;
import com.company.rotations.models.DecisionOutput;
import com.company.rotations.models.RotationState;

public interface ActionExecutorService {
    RotationState execute(Alert alert, DecisionOutput decision);
}
```

**AlertAdapter** (`shared/spi/alerting/AlertAdapter.java`)

```java
package com.company.rotations.spi.alerting;

import com.company.rotations.models.Alert;

public interface AlertAdapter {
    String getProviderName();
    Alert adapt(String rawPayload);
}
```

**NotificationChannel** (`shared/spi/notification/NotificationChannel.java`)

```java
package com.company.rotations.spi.notification;

public interface NotificationChannel {
    String getChannelType();
    void send(String recipient, String subject, String body);
}
```

### Package Naming

Se adopta un prefijo unico `com.company.rotations.` con subpaquetes por modulo:

- `com.company.rotations.models.*` — modelos de dominio en shared-models
- `com.company.rotations.spi.*` — interfaces en shared-spi
- `com.company.rotations.alerting.*` — alert-integrator
  - `controller/`, `adapter/`, `dedup/`, `worker/`, `dlq/`
- `com.company.rotations.verification.*` — verification-engine
  - `provider/`, `verifier/`, `calculator/`
- `com.company.rotations.decision.*` — decision-engine
  - `engine/`, `playbook/`, `drools/`
- `com.company.rotations.action.*` — action-executor
  - `rotation/`, `notification/`, `executor/`
- `com.company.rotations.logging.*` — logging (transversal, en modulo propio)
- `com.company.rotations.config.*` — configuracion (transversal)

### Enforcement de Build en Maven

Maven garantiza orden de compilacion automaticamente gracias al DAG de dependencias. El parent POM define los modulos en orden topologico:

```xml
<modules>
    <module>shared/shared-models</module>
    <module>shared/shared-spi</module>
    <module>alert-integrator</module>
    <module>verification-engine</module>
    <module>decision-engine</module>
    <module>action-executor</module>
</modules>
```

**Regla:** Si un modulo intenta importar una dependencia que no esta declarada, Maven falla en compile. Esto actua como enforcement de limites de modulo.

**Limiter importaciones cruzadas:**

- `alert-integrator` NO puede importar paquetes de `verification-engine`, `decision-engine` o `action-executor`.
- `verification-engine` NO puede importar paquetes de `decision-engine` o `action-executor`.
- `decision-engine` NO puede importar paquetes de `action-executor`.
- Los modulos SOLO pueden importar de sus dependencias directas en el POM.

Si se necesita una dependencia transitoria (ej: `action-executor` necesita algo de `alert-integrator`), debe pasar por el contract layer en `shared/spi/`.

## Decisiones BLOQUE C — ADOPTADAS (Cross-Cutting Concerns)

### Global Error Handling

Se adopta `@ControllerAdvice` + `@ExceptionHandler` para manejo centralizado de errores. Esto garantiza:

- **Consistencia:** Todas las respuestas de error siguen el mismo formato JSON.
- **Seguridad:** Los errores no exponen informacion sensible (stack traces, query internals).
- **Observabilidad:** Todos los errores se registran con contexto MDC (alertId, tenantId, sessionId).

#### Formato de Respuesta de Error

```json
{
    "timestamp": "2026-08-09T12:34:56.789Z",
    "status": 400,
    "error": "Bad Request",
    "path": "/api/v1/alerts",
    "message": "Validacion fallida: credentialType es obligatorio",
    "details": [
        {
            "field": "credentialType",
            "message": "debe ser uno de: [AWS_ACCESS_KEY, IAM_USER, RDS_CREDENTIAL]"
        }
    ]
}
```

#### Jerarquia de Excepciones Personalizadas

```
RuntimeException
├── BusinessException (4xx client errors)
│   ├── BadRequestException (400)
│   │   ├── InvalidInputException
│   │   └── ValidationException
│   ├── UnauthorizedException (401)
│   ├── ForbiddenException (403)
│   ├── NotFoundException (404)
│   └── ConflictException (409)
├── TechnicalException (5xx server errors)
│   ├── DatabaseException (500)
│   ├── ExternalServiceException (502/503)
│   └── TimeoutException (504)
└── SystemException (500 internal — errores inesperados)
```

#### ControllerAdvice Basico

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex, HttpServletRequest request) {
        
        log.warn("Business error: path={}, status={}, message={}",
                 request.getRequestURI(), ex.getStatus(), ex.getMessage());
        
        return ResponseEntity
            .status(ex.getStatus())
            .body(ErrorResponse.from(ex));
    }

    @ExceptionHandler(TechnicalException.class)
    public ResponseEntity<ErrorResponse> handleTechnicalException(
            TechnicalException ex, HttpServletRequest request) {
        
        log.error("Technical error: path={}", request.getRequestURI(), ex);
        
        return ResponseEntity
            .status(500)
            .body(ErrorResponse.internalServerError());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception ex, HttpServletRequest request) {
        
        log.error("Unexpected error: path={}", request.getRequestURI(), ex);
        
        return ResponseEntity
            .status(500)
            .body(ErrorResponse.internalServerError());
    }
}
```

### API Versioning

Se adopta **URL versioning** con prefijo `/api/v1/`:

```
POST /api/v1/alerts          — Ingestar alerta
GET  /api/v1/verification/{alertId}  — Estado de verificacion
POST /api/v1/decisions       — Ejecutar decision engine
POST /api/v1/actions         — Ejecutar accion
GET  /api/v1/admin/rules     — Admin: gestionar reglas Drools
GET  /actuator/health        — Health check (sin version, interno)
```

**Por que URL versioning:**
- Explicito y facil de entender para consumidores.
- Facilita migration path cuando se cambie a `/api/v2/`.
- Compatible con routing de Spring MVC.
- Los health checks (`/actuator/health`) NO se versionan (son internos).

**Evolucion de la API:**
- `v1` — Version actual del proyecto
- `v2` — Se crea solo cuando hay breaking changes en los endpoints (no en los modelos internos)
- Ambos versions pueden coexistir temporalmente durante la migration

### Security

Se adopta una estrategia de **seguridad por capas** (YAGNI para Phase 1):

| Capa | Mechanismo | Detalle |
|---|---|---|
| **Webhooks** | HMAC-SHA256 signature validation | Header `X-Signature` + shared secret |
| **Admin endpoints** | API Key en header `X-API-Key` | Key almacenada en env var / secret vault |
| **Health checks** | Sin autenticacion | `/actuator/health`, `/health` |
| **Interno** | Spring Security (disabled en Phase 1) | Activo cuando se agreguen usuarios |

**Spring Security en Phase 1:**
- **No se activa** por defecto en Phase 1 (sin usuarios ni roles).
- Se incluye la dependency `spring-boot-starter-security` pero con config minimal para que no bloquee endpoints.
- Se activa cuando se necesite autenticacion de usuarios (Phase 2+).

#### Webhook Signature Validation

```java
@Component
public class SignatureValidator {

    private static final String SIGNATURE_HEADER = "X-Signature";
    private static final String ALGORITHM = "HmacSHA256";

    private final String webhookSecret;

    public SignatureValidator(@Value("${webhook.secret}") String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public boolean validate(HttpServletRequest request, String payload) {
        String signature = request.getHeader(SIGNATURE_HEADER);
        if (signature == null || signature.isBlank()) {
            return false;
        }

        Mac mac = Mac.getInstance(ALGORITHM);
        mac.init(new SecretKeySpec(webhookSecret.getBytes(), ALGORITHM));
        byte[] expected = mac.doFinal(payload.getBytes());

        return ConstantTimeUtils.equals(signature.getBytes(), expected);
    }
}
```

#### IP Validation (opcional, para proveedores como GitGuardian)

```java
@Component
public class IpValidator {

    private final Set<String> allowedIps;

    public IpValidator(@Value("#{'${webhook.allowed-ips:}'.split(',')}") Set<String> allowedIps) {
        this.allowedIps = allowedIps;
    }

    public boolean validate(HttpServletRequest request) {
        String clientIp = extractClientIp(request);
        return allowedIps.isEmpty() || allowedIps.contains(clientIp);
    }
}
```

### CORS

Se adopta **CORS estricto** — solo permitir origenes explicitamente configurados:

```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins(System.getenv("ALLOWED_ORIGINS")) // o application.yml
            .allowedMethods("GET", "POST", "PUT", "DELETE")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600);
    }
}
```

**Reglas:**
- En `dev` — permitir `*` (localhost).
- En `staging/prod` — solo permitir origenes explicitos via variable de entorno o config.
- Los endpoints `/actuator/**` no necesitan CORS (son internos).

### Config Management

Se adopta **`application.yml` + Spring Profiles** para gestion de configuracion:

```
src/main/resources/
├── application.yml              (config comun a todos los perfiles)
├── application-dev.yml          (perfil dev — H2 en memoria, logs debug)
├── application-staging.yml      (perfil staging — Postgres, logging info)
└── application-prod.yml         (perfil prod — Postgres, logging warn+)
```

#### application.yml (comun)

```yaml
spring:
  application:
    name: openstack-key-rotation
  jpa:
    hibernate:
      ddl-auto: validate  # Nunca auto-create en prod
    open-in-view: false   # Prevenir lazy loading fuera de transaction
  jackson:
    serialization:
      write-dates-as-timestamps: false
    default-property-inclusion: non_null

# Actuator
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized
```

#### application-dev.yml

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:devdb;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
    hibernate:
      ddl-auto: create-drop
  jackson:
    serialization:
      indent-output: true

logging:
  level:
    root: DEBUG
    com.company.rotations: DEBUG
```

#### application-staging.yml / application-prod.yml

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:key_rotation}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: false
        use_sql_comments: false

logging:
  level:
    root: WARN
    com.company.rotations: INFO
```

**Variables de entorno criticas:**

| Variable | Descripcion | Requerido |
|---|---|---|
| `DB_HOST` | Host de PostgreSQL | Si |
| `DB_PORT` | Puerto de PostgreSQL | No (default: 5432) |
| `DB_NAME` | Nombre de la base de datos | No (default: key_rotation) |
| `DB_USERNAME` | Usuario de PostgreSQL | Si |
| `DB_PASSWORD` | Password de PostgreSQL | Si |
| `WEBHOOK_SECRET` | Shared secret para HMAC validation | Si |
| `ALLOWED_ORIGINS` | Origenes CORS permitidos (comma-separated) | No (default: none) |
| `AWS_REGION` | Region de AWS (us-east-1, etc.) | Si |
| `AWS_ACCESS_KEY_ID` | AWS credentials (desde Vault/env) | Si |
| `AWS_SECRET_ACCESS_KEY` | AWS credentials (desde Vault/env) | Si |

### Request Validation

Se adopta **Bean Validation** con `@Valid` y annotations JSR-380:

```java
public class AlertRequest {

    @NotBlank(message = "providerName es obligatorio")
    private String providerName;

    @NotBlank(message = "credentialType es obligatorio")
    @EnumValue(enumClass = CredentialType.class, message = "debe ser: AWS_ACCESS_KEY, IAM_USER, RDS_CREDENTIAL")
    private String credentialType;

    @NotBlank(message = "tenantId es obligatorio")
    private String tenantId;

    @NotNull(message = "credentials es obligatorio")
    private Credentials credentials;

    @Valid
    private List<SeverityRule> severityRules;
}
```

**Controller:**

```java
@PostMapping("/alerts")
public ResponseEntity<AlertResponse> ingestAlert(
        @Valid @RequestBody AlertRequest request,
        BindingResult bindingResult) {

    if (bindingResult.hasErrors()) {
        throw new ValidationException(bindingResult);
    }

    Alert alert = alertService.ingest(request);
    return ResponseEntity.status(201).body(AlertResponse.from(alert));
}
```

#### Validacion Custom

Para validaciones complejas (ej: webhook payload schema), se crean annotations custom:

```java
@Target({FIELD})
@Retention(RUNTIME)
@Constraint(validatedBy = WebhookPayloadValidator.class)
public @interface ValidWebhookPayload {
    String message() default "Payload no valido para el proveedor especificado";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] providerClass();
}
```

## Decisiones BLOQUE D — ADOPTADAS (Testing Strategy)

### Framework Principal

Se adopta **JUnit 5 + Mockito + AssertJ** como stack de testing:

| Herramienta | Uso | Razon |
|---|---|---|
| **JUnit 5** | Framework base para unit tests | Soporte nativo en Spring Boot 3.x, extensions |
| **Mockito** | Mock de interfaces y dependencias | Integracion con JUnit 5 (mockito-junit-jupiter) |
| **AssertJ** | Assertions fluent y tipadas | Assertions mas legibles y detectan errores en compile-time |
| **Spring Boot Test** | Integration tests con contexto Spring | `@SpringBootTest`, `@WebMvcTest`, `@DataJpaTest` |
| **Testcontainers** | Integration tests con Postgres real | Evita H2 dialect mismatch, tests mas fieles a prod |

#### Dependency de Testing (en parent POM)

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
    <exclusions>
        <exclusion>
            <groupId>org.junit.vintage</groupId>
            <artifactId>junit-vintage-engine</artifactId>
        </exclusion>
    </exclusions>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

### Estrategia de Testing por Capa

#### 1. Unit Tests (80%+ cobertura de logica de dominio)

Tests que NO inicializan Spring Context. Solo testean clases individuales con mocks:

```java
@ExtendWith(MockitoExtension.class)
class AlertProcessorTest {

    @Mock
    private VerificationProvider verificationProvider;

    @Mock
    private DecisionEvaluator decisionEvaluator;

    @InjectMocks
    private AlertProcessor alertProcessor;

    @Test
    void processAlert_validAlert_returnsDecisionOutput() {
        // Given
        Alert alert = new Alert("gitguardian", "AWS_ACCESS_KEY", "tenant-1");
        VerificationResult verification = new VerificationResult(alert.getId(), true, "verified");
        DecisionOutput decision = new DecisionOutput(alert.getId(), "rotate", "high");

        when(verificationProvider.verify(alert, "AWS_ACCESS_KEY")).thenReturn(verification);
        when(decisionEvaluator.evaluate(alert, verification)).thenReturn(decision);

        // When
        DecisionOutput result = alertProcessor.processAlert(alert);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getDecision()).isEqualTo("rotate");
        assertThat(result.getSeverity()).isEqualTo("high");
        verify(verificationProvider).verify(alert, "AWS_ACCESS_KEY");
        verify(decisionEvaluator).evaluate(alert, verification);
    }

    @Test
    void processAlert_verificationFails_returnsNoAction() {
        // Given
        Alert alert = new Alert("gitguardian", "AWS_ACCESS_KEY", "tenant-1");
        VerificationResult verification = new VerificationResult(alert.getId(), false, "expired");

        when(verificationProvider.verify(alert, "AWS_ACCESS_KEY")).thenReturn(verification);

        // When
        DecisionOutput result = alertProcessor.processAlert(alert);

        // Then
        assertThat(result.getDecision()).isEqualTo("no_action");
        assertThat(result.getReason()).isEqualTo("credential already expired — no rotation needed");
    }
}
```

#### 2. Integration Tests con Testcontainers (Postgres real)

Tests que inicializan Spring Context + Testcontainers PostgreSQL:

```java
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class AlertRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private AlertRepository alertRepository;

    @Test
    void saveAndFindById_alertPersisted() {
        Alert alert = new Alert("gitguardian", "AWS_ACCESS_KEY", "tenant-1");
        Alert saved = alertRepository.save(alert);

        assertThat(saved.getId()).isNotNull();
        assertThat(alertRepository.findById(saved.getId())).isPresent();
    }

    @Test
    void findByTenantId_multipleAlerts_returnsAll() {
        alertRepository.save(new Alert("gitguardian", "AWS_ACCESS_KEY", "tenant-1"));
        alertRepository.save(new Alert("gitguardian", "IAM_USER", "tenant-1"));
        alertRepository.save(new Alert("gitguardian", "RDS_CREDENTIAL", "tenant-2"));

        var results = alertRepository.findByTenantId("tenant-1");
        assertThat(results).hasSize(2);
    }
}
```

#### 3. Web Tests (MockMvc para endpoints REST)

Tests que validan endpoints HTTP sin levantar servidor real:

```java
@WebMvcTest(AlertController.class)
class AlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AlertService alertService;

    @Test
    void ingestAlert_validPayload_returns201() throws Exception {
        AlertRequest request = new AlertRequest("gitguardian", "AWS_ACCESS_KEY", "tenant-1");
        Alert savedAlert = new Alert("gitguardian", "AWS_ACCESS_KEY", "tenant-1");
        savedAlert.setId("alert-1");

        when(alertService.ingest(any(AlertRequest.class))).thenReturn(savedAlert);

        mockMvc.perform(post("/api/v1/alerts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "providerName": "gitguardian",
                        "credentialType": "AWS_ACCESS_KEY",
                        "tenantId": "tenant-1"
                    }
                """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value("alert-1"))
            .andExpect(jsonPath("$.providerName").value("gitguardian"));

        verify(alertService).ingest(any(AlertRequest.class));
    }

    @Test
    void ingestAlert_missingField_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/alerts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "providerName": "gitguardian"
                    }
                """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void ingestAlert_invalidSignature_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/alerts")
                .header("X-Signature", "invalid-signature")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "providerName": "gitguardian",
                        "credentialType": "AWS_ACCESS_KEY",
                        "tenantId": "tenant-1"
                    }
                """))
            .andExpect(status().isUnauthorized());
    }
}
```

#### 4. Drools Testing

Se adopta **`KieContainer` testable con `@KieGuvnorResource`** para pruebas de reglas:

```java
@ExtendWith(MockitoExtension.class)
class DroolsRuleEngineTest {

    @Autowired
    private KieContainer kieContainer;

    @Test
    void secretExposed_highSeverity_returnsRotateDecision() {
        // Given
        Alert alert = new Alert("gitguardian", "AWS_ACCESS_KEY", "tenant-1");
        VerificationResult verification = new VerificationResult(alert.getId(), true, "verified");

        // When
        KieSession session = kieContainer.newKieSession();
        session.insert(alert);
        session.insert(verification);
        session.fireAllRules();
        session.dispose();

        // Then
        // Verificar que se genero la decision correcta
        // (la assertion depende del modelo de DecisionOutput)
    }
}
```

**Reglas Drools en tests:**
- Los archivos `.drl` se cargan desde `src/test/resources/rules/` en cada modulo decision-engine.
- Se usa `KieFileSystem` para cargar reglas dinamicamente en tests:

```java
@Test
void customRule_severityCalculatedCorrectly() throws Exception {
    KieContainer kieContainer = createKieContainerFromResources("rules/test-rules.drl");
    KieSession session = kieContainer.newKieSession();
    // ... test logic
}
```

### Test Profiles

| Profile | Uso | Base de datos | Logging |
|---|---|---|---|
| `test` (default) | Tests de integration | Testcontainers PostgreSQL | WARN |
| `dev` | Desarrollo local | H2 en memoria | DEBUG |
| `staging` | Staging | Postgres real (staging) | INFO |
| `prod` | Produccion | Postgres real (prod) | WARN+ |

#### application-test.yml

```yaml
spring:
  datasource:
    url: jdbc:tc:postgresql:16:///testdb?TC_DAEMON=true
    driver-class-name: org.testcontainers.jdbc.TCDriver
  jpa:
    hibernate:
      ddl-auto: create-drop
    database-platform: org.hibernate.dialect.PostgreSQLDialect

logging:
  level:
    root: WARN
    com.company.rotations: INFO
```

### Webhook Testing

Se usa **MockMvc** con verificacion de signatures HMAC:

```java
@Test
void webhook_validSignature_returns200() throws Exception {
    String payload = """
        {"providerName":"gitguardian","credentialType":"AWS_ACCESS_KEY","tenantId":"tenant-1"}
    """;
    String signature = generateHmacSha256(payload, webhookSecret);

    mockMvc.perform(post("/api/v1/alerts")
            .header("X-Signature", signature)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isOk());
}

@Test
void webhook_invalidSignature_returns401() throws Exception {
    mockMvc.perform(post("/api/v1/alerts")
            .header("X-Signature", "invalid-signature")
            .contentType(MediaType.APPLICATION_JSON)
            .content("payload"))
        .andExpect(status().isUnauthorized());
}
```

### Coverage Target

Se establece un **coverage minimo del 70%** para codigo de dominio, con las siguientes reglas:

| Tipo de codigo | Coverage minimo | Razon |
|---|---|---|
| **Logic de dominio** (services, processors, calculators) | 80%+ | Core del negocio, debe ser robusto |
| **Controllers / Adapters** | 60%+ | Thin layer, MockMvc cubre los endpoints principales |
| **Config classes** | 50%+ | Generalmente simples, faciles de probar |
| **Models / DTOs** | 90%+ | POJOs simples, testean equality/toString |
| **Exceptions** | 90%+ | POJOs simples, testean mensaje y status |

**Medicion con JaCoCo:**

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
    <executions>
        <execution>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

**En CI/CD:**
- El build falla si coverage general < 70% o coverage de `src/main/java` de dominio < 80%.
- Coverage minimo se configura en `pom.xml` con `<rule>` de JaCoCo.

### Testing por Modulo

Cada modulo tiene su propia capa de tests en `src/test/java`:

```
alert-integrator/
├── src/test/java/.../
│   ├── controller/AlertControllerTest.java
│   ├── adapter/
│   │   ├── GitGuardianAdapterTest.java
│   │   └── AdapterRegistryTest.java
│   ├── dedup/EventDedupTest.java
│   └── worker/WorkerPoolTest.java
└── src/test/resources/
    └── application-test.yml

verification-engine/
├── src/test/java/.../
│   ├── verifier/CredentialVerifierTest.java
│   ├── provider/
│   │   └── AwsStsProviderTest.java
│   └── calculator/BlastRadiusCalculatorTest.java
└── src/test/resources/
    └── application-test.yml

decision-engine/
├── src/test/java/.../
│   ├── engine/RuleEngineTest.java
│   ├── playbook/PlaybookManagerTest.java
│   └── drools/DroolsRuleEngineTest.java
└── src/test/resources/
    ├── rules/
    │   ├── test-rules-1.drl
    │   └── test-rules-2.drl
    └── application-test.yml

action-executor/
├── src/test/java/.../
│   ├── rotation/RotationStateMachineTest.java
│   ├── executor/AwsRotationServiceTest.java
│   └── notification/NotificationDispatcherTest.java
└── src/test/resources/
    └── application-test.yml
```

## Decisiones BLOQUE E — ADOPTADAS (Vault / Secret Management)

### Decision: HIBRIDO — AWS Secrets Manager + PostgreSQL encrypted

Se adopta una estrategia hibrida que equilibra simplicidad para Phase 1 con seguridad adecuada para production:

| Tipo de secreto | Almacenamiento | Acceso | Razon |
|---|---|---|---|
| **Credenciales de cliente** (AWS keys, DB creds, webhook secrets) | AWS Secrets Manager | AWS SDK v2 (SecretsManagerClient) | Cifrado nativo AWS, rotation automatica, audit via CloudTrail |
| **Configuracion de la app** (DB password, JWT keys) | AWS Secrets Manager | Env vars + AWS SDK | No se commit de ningun secret |
| **Credenciales en alert payload** | PostgreSQL (AES-256 en columna) | Desencriptado al vuelo por JPA @ColumnTransformer | Los payloads de alerts son volatiles, no necesitan rotation automatica |

---

### Punto 7 — Gestion de Credenciales Admin del Cliente (distinto a app secrets)

Estas son las credenciales **read-only** que almacenamos de cada cliente para poder verificar su infraestructura cloud (STS, Azure AD, etc.). Son el core del negocio.

#### Doble capa de proteccion

```
┌─────────────────────────────────────────────────────────────────────┐
│                  Credenciales Admin del Cliente                     │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  Capa 1: AWS Secrets Manager (produccion)                          │
│  ├── /clients/{tenant-id}/aws-access-key       (access key ID)     │
│  ├── /clients/{tenant-id}/aws-secret-key         (secret access)   │
│  ├── /clients/{tenant-id}/aws-region             (region default)  │
│  └── /clients/{tenant-id}/metadata               (extra config)    │
│                                                                     │
│  Capa 2: PostgreSQL encrypted (backup / fallback)                   │
│  └── client_credentials_table                                       │
│      ├── client_id (UUID PK)                                        │
│      ├── tenant_id (VARCHAR, unique)                                │
│      ├── provider (ENUM: AWS, AZURE, GCP)                          │
│      ├── access_key_encrypted (AES-256)                             │
│      ├── secret_key_encrypted (AES-256)                             │
│      ├── region (VARCHAR)                                           │
│      ├── additional_config (JSONB)                                  │
│      ├── vault_ref (VARCHAR — path en Secrets Manager)             │
│      ├── created_at, updated_at                                     │
│      └── rotated_at (timestamp — auditoria de rotation)            │
│                                                                     │
│  Reglas de seguridad:                                               │
│  ├── Nunca almacenar en .env, git, logs, o errores HTTP            │
│  ├── Acceso via IAM role minimo (solo secretsmanager:GetSecretValue)│
│  ├── Encryption en repositorio: NUNCA (secrets nunca van al repo)  │
│  ├── Encryption en DB: AES-256 (columnas individuales)             │
│  └── Audit trail: cada acceso queda registrado en audit_events     │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

#### POC vs Produccion

| Aspecto | POC ($0) | Produccion (AWS) |
|---------|----------|------------------|
| **Almacenamiento** | `.env` por tenant + DB encrypted | AWS Secrets Manager |
| **Acceso** | `System.getenv()` + decryption local | `SecretsManagerClient` con IAM role |
| **Rotation** | Manual (admin actualiza .env) | Automatica cada 90 dias via Lambda |
| **Audit** | `audit_events` table | CloudTrail + `audit_events` table |
| **Key rotation key** | Env var `ENCRYPTION_MASTER_KEY` | Secrets Manager `/app/rotation/encryption-key` |

**Para el POC no se necesitan credenciales AWS reales.** Se trabaja con:
- `.env.{tenant}` files locales (nunca en git, agregados a `.gitignore`)
- Credenciales dummy para test de integracion
- La estructura de `SecretVaultService` existe y funciona igual, solo cambia el backend (mock en POC, AWS SDK en produccion)

#### Rotation de Credenciales de Cliente

```java
@Service
public class TenantCredentialRotationService {

    private final SecretVaultService vault;
    private final TenantCredentialRepository credentialRepo;
    private final AuditService audit;

    /**
     * Rotate credentials for a tenant.
     * Step 1: Generate new credentials (via provider API)
     * Step 2: Store new in Secrets Manager (dual-write)
     * Step 3: Update DB encrypted backup
     * Step 4: Verify new credentials work
     * Step 5: Delete old from Secrets Manager
     * Step 6: Audit trail
     */
    @Transactional
    public RotationResult rotateCredentials(String tenantId) {
        // Step 1: Read current
        var current = vault.getTenantCredentials(tenantId);

        // Step 2: Generate new (call provider API — AWS STS CreateAccessKey)
        var newCredentials = providerApi.generateNewCredentials(tenantId);

        // Step 3: Dual-write — store new in vault and encrypted DB
        vault.putTenantCredentials(tenantId, newCredentials);
        var stored = new TenantCredential(tenantId, newCredentials, "/clients/" + tenantId);
        credentialRepo.save(stored);

        // Step 4: Verify
        if (!providerApi.verify(newCredentials)) {
            audit.log("CREDENTIAL_ROTATION_FAILED", tenantId, "New credentials verification failed");
            throw new TechnicalException("Credential rotation failed: new credentials invalid");
        }

        // Step 5: Delete old (vault handles versioning)
        vault.archiveOldVersion(tenantId);

        // Step 6: Audit
        audit.log("CREDENTIAL_ROTATED", tenantId, Map.of(
            "rotated_at", Instant.now(),
            "vault_path", "/clients/" + tenantId
        ));

        return new RotationResult(true, Instant.now());
    }
}
```

**Reglas de rotation:**
- **Frecuencia:** cada 90 dias (configurable por tenant)
- **Mecanismo POC:** manual via admin endpoint o script
- **Mecanismo produccion:** AWS Secrets Manager Lambda trigger automatic
- **Fallback:** si rotation falla > 3 intentos, se escala a humano (notificacion Slack/email)
- **Downtime:** zero — dual-write asegura que ambas keys funcionan durante la transicion

### Arquitectura de Secretos

```
┌─────────────────────────────────────────────────────────────────┐
│                    Secret Management Architecture                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  AWS Secrets Manager                                            │
│  ├── /app/rotation/db-password          (Postgres password)     │
│  ├── /app/rotation/webhook-secret         (HMAC key)            │
│  ├── /app/rotation/jwt-signing-key        (JWT signing)         │
│  └── /app/rotation/aws-creds/{tenant}     (AWS creds por tenant)│
│                                                                 │
│  PostgreSQL (encrypted columns)                                 │
│  └── credentials_table                                           │
│      ├── credential_id (UUID PK)                                │
│      ├── tenant_id (VARCHAR)                                    │
│      ├── credential_type (ENUM)                                 │
│      ├── secret_key (VARCHAR — AES-256 encrypted)               │
│      └── metadata (JSONB)                                       │
│                                                                 │
│  Env Variables (solo en CI/CD y deploy)                         │
│  ├── AWS_REGION=us-east-1                                      │
│  ├── AWS_DEFAULT_REGION=us-east-1                              │
│  └── AWS_ROLE_ARN=arn:aws:iam::.../role/rotation-role          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### AWS Secrets Manager Integration

```java
@Service
public class SecretVaultService {

    private final SecretsManagerClient secretsManagerClient;
    private final String secretBasePath;

    public SecretVaultService(
            @Value("${vault.base-path:/app/rotation}") String secretBasePath) {
        this.secretsManagerClient = SecretsManagerClient.builder().build();
        this.secretBasePath = secretBasePath;
    }

    public String getSecret(String path) {
        String fullKey = secretBasePath + "/" + path;
        
        GetSecretValueRequest request = GetSecretValueRequest.builder()
            .secretId(fullKey)
            .build();
        
        GetSecretValueResponse response = secretsManagerClient.getSecretValue(request);
        return response.secretString();
    }

    public Map<String, String> getTenantCredentials(String tenantId) {
        String fullKey = secretBasePath + "/aws-creds/" + tenantId;
        
        GetSecretValueRequest request = GetSecretValueRequest.builder()
            .secretId(fullKey)
            .build();
        
        GetSecretValueResponse response = secretsManagerClient.getSecretValue(request);
        return objectMapper.readValue(response.secretString(), Map.class);
    }

    public void rotateSecret(String path, String newSecret) {
        String fullKey = secretBasePath + "/" + path;
        
        PutSecretValueRequest request = PutSecretValueRequest.builder()
            .secretId(fullKey)
            .secretString(newSecret)
            .build();
        
        secretsManagerClient.putSecretValue(request);
    }
}
```

### PostgreSQL Encrypted Columns (para alerts con credenciales)

Los alert payloads contienen credenciales temporales (AWS access keys, tokens). Estas se almacenan en la base de datos con cifrado AES-256:

```java
@Entity
@Table(name = "credentials")
public class Credential {

    @Id
    private String credentialId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "credential_type", nullable = false)
    private CredentialType credentialType;

    @Column(name = "secret_key", nullable = false, columnDefinition = "VARCHAR(4096)")
    private String encryptedSecretKey;

    @Column(name = "metadata", columnDefinition = "JSONB")
    private String metadata;

    // Encryption/decryption via JPA AttributeConverter
    @Convert(converter = Aes256Converter.class)
    @Column(name = "secret_key", nullable = false)
    private String secretKey;

    @PrePersist
    @PreUpdate
    public void encrypt() {
        if (secretKey != null && !secretKey.isEmpty()) {
            encryptedSecretKey = Aes256Encryptor.encrypt(secretKey);
        }
    }

    @PostLoad
    public void decrypt() {
        if (encryptedSecretKey != null && !encryptedSecretKey.isEmpty()) {
            secretKey = Aes256Encryptor.decrypt(encryptedSecretKey);
        }
    }
}
```

#### Aes256Encryptor — Cifrado AES-256

```java
public final class Aes256Encryptor {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";
    private static final String KEY_SOURCE = "vault.key:encryption-master-key";

    private static volatile SecretKey key;

    private Aes256Encryptor() {}

    private static SecretKey getKey(SecretVaultService vault) {
        if (key == null) {
            synchronized (Aes256Encryptor.class) {
                if (key == null) {
                    String masterKey = vault.getSecret(KEY_SOURCE);
                    byte[] decoded = Base64.getDecoder().decode(masterKey);
                    key = new SecretKeySpec(decoded, ALGORITHM);
                }
            }
        }
        return key;
    }

    public static String encrypt(String plaintext) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getKey());
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (GeneralSecurityException e) {
            throw new TechnicalException("Failed to encrypt secret", e);
        }
    }

    public static String decrypt(String ciphertext) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, getKey());
            byte[] decoded = Base64.getDecoder().decode(ciphertext);
            byte[] decrypted = cipher.doFinal(decoded);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new TechnicalException("Failed to decrypt secret", e);
        }
    }
}
```

**Nota sobre ECB mode:** ECB es adequate solo para datos que NO tienen patrones repetitivos (como claves aleatorias). Para datos con estructura predecible, se debe usar CBC con IV almacenado junto con el ciphertext.

### Secret Rotation en AWS Secrets Manager

AWS Secrets Manager soporta rotation automatica via Lambda:

```
AWS Secrets Manager
    ├── /app/rotation/aws-creds/tenant-1
    │   ├── RotationLambda: RotateTenantCredentialsLambda
    │   ├── Schedule: Every 90 days
    │   └── Auto-rotate: Enabled
    │
    ├── /app/rotation/aws-creds/tenant-2
    │   ├── RotationLambda: RotateTenantCredentialsLambda
    │   ├── Schedule: Every 90 days
    │   └── Auto-rotate: Enabled
```

**Lambda de rotation (concepto):**

```java
public class RotateTenantCredentialsLambda implements RequestHandler<SecretsManagerEvent, SecretsManagerResponse> {

    @Override
    public SecretsManagerResponse handleRequest(SecretsManagerEvent event, Context context) {
        String secretId = event.secretId;
        String step = event.requestType;

        switch (step) {
            case "createSecret":
                return createNewSecret(secretId);
            case "setSecret":
                return setSecret(secretId);
            case "testSecret":
                return testSecret(secretId);
            case "finishSecret":
                return finishSecret(secretId);
            default:
                throw new IllegalArgumentException("Unknown step: " + step);
        }
    }

    private SecretsManagerResponse createNewSecret(String secretId) {
        // 1. Obtener credenciales actuales
        // 2. Crear nuevas credenciales en AWS IAM
        // 3. Almacenar nuevas credenciales como AWSPENDING
        // 4. Notificar al sistema de rotation para replicar en DB
        return new SecretsManagerResponse();
    }
}
```

### Configuracion de Secrets

```yaml
# application.yml
vault:
  base-path: /app/rotation
  aws-region: ${AWS_REGION:us-east-1}
  rotation:
    schedule: "0 0 0 */90 * ?"  # Cada 90 dias
    max-age-days: 90
    rotation-lambda-arn: arn:aws:lambda:us-east-1:...:function:RotateTenantCredentials

# application-prod.yml
vault:
  base-path: /app/rotation
  encryption:
    key-path: vault.key:encryption-master-key  # Master key en Secrets Manager
    algorithm: AES/ECB/PKCS5Padding
```

### Security Considerations

| Aspecto | Implementacion |
|---|---|
| **Secrets en repositorio** | Nunca. `.env` y `application-prod.yml` en `.gitignore` |
| **Secrets en logs** | Redaccion automatica (Logback mask de patrones `AKIA.*`, `(?i)password`, `(?i)secret`) |
| **Secrets en memoria** | `char[]` en vez de `String` para claves sensibles (se puede limpiar con `Arrays.fill()`) |
| **Acceso a Secrets Manager** | IAM role con permiso minimo: `secretsmanager:GetSecretValue`, `secretsmanager:PutSecretValue` |
| **Cifrado en DB** | AES-256 con key almacenada en Secrets Manager (no hardcodeada) |
| **Rotation automatica** | AWS Secrets Manager rotation schedule (cada 90 dias por defecto) |
| **Audit** | CloudTrail logs para acceso a Secrets Manager |

### Alternativas Evaluadas

| Opcion | Phase 1 | Phase 2+ | Razon |
|---|---|---|---|
| **AWS Secrets Manager** | ✅ Adoptado | ✅ Mantener | Costo minimo ($0.40/secret/mes + requests), nativo AWS |
| **Jasypt + env vars** | ❌ No | ❌ No | Menos seguro, sin rotation automatica, sin audit |
| **HashiCorp Vault** | ❌ No | ✅ Evaluar | Overkill para Phase 1, requiere infra adicional |
| **AWS Systems Manager Parameter Store** | ❌ No | ✅ Evaluar | Mas barato pero menos features (sin rotation automatica) |

### Dependency en Parent POM

```xml
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>secretsmanager</artifactId>
</dependency>
```

## Decisiones BLOQUE F — ADOPTADAS (Deployment)

### Docker — Single Container + Docker Compose

Se adopta un enfoque de **single container para la app + docker-compose para la infraestructura local**:

```
┌─────────────────────────────────────────────────────────────────┐
│                     Docker Compose (Local/Staging)               │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  openstack-key-rotation-app  (Docker image, Java 21, Spring Boot)│
│  ├── Expose: 8080 (HTTP)                                        │
│  ├── Health: /actuator/health                                   │
│  └── Volumes: /app/config (for config files)                    │
│                                                                 │
│  postgres-16                   (PostgreSQL 16)                   │
│  ├── Volumes: pg_data:/var/lib/postgresql/data                  │
│  └── Health: pg_isready                                         │
│                                                                 │
│  ────────────────────────────────────────────────────────────   │
│                                                                 │
│  (Phase 2+)                                                     │
│  aws-secrets-manager         (Managed service, no container)     │
│  aws-iam                     (Managed service, no container)     │
│  cloudwatch-logs             (Managed service, no container)     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

#### Dockerfile (Multi-stage build)

```dockerfile
# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY shared shared
COPY alert-integrator alert-integrator
COPY verification-engine verification-engine
COPY decision-engine decision-engine
COPY action-executor action-executor
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre
WORKDIR /app

ARG USER=appuser
RUN groupadd -r ${USER} && useradd -r -g ${USER} ${USER}

COPY --from=build /app/*/target/*.jar app.jar
COPY --from=build /app/shared/shared-spi/target/*.jar /app/spi/

RUN chown -R ${USER}:${USER} /app

USER ${USER}

EXPOSE 8080

ENTRYPOINT ["java", \
    "-jar", "app.jar", \
    "--spring.profiles.active=${SPRING_PROFILES_ACTIVE:prod}", \
    "--server.port=${SERVER_PORT:8080}"]
```

#### docker-compose.yml

```yaml
version: '3.8'

services:
  app:
    build:
      context: .
      dockerfile: Dockerfile
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=dev
      - AWS_REGION=us-east-1
      - DB_HOST=postgres
      - DB_PORT=5432
      - DB_NAME=key_rotation
      - DB_USERNAME=${DB_USERNAME}
      - DB_PASSWORD=${DB_PASSWORD}
      - WEBHOOK_SECRET=${WEBHOOK_SECRET}
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: key_rotation
      POSTGRES_USER: ${DB_USERNAME}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - pg_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USERNAME}"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  pg_data:
```

### Cloud Provider

Se adopta **AWS** exclusivamente para Phase 1 (constraint del proyecto):

| Servicio | Uso | Alternativa si escala |
|---|---|---|
| **ECS Fargate** | Container orchestration (sin servidores) | EKS (si se necesitan pods Kubernetes) |
| **RDS PostgreSQL** | Base de datos gestionada | Self-hosted en EC2 (no recomendado) |
| **Secrets Manager** | Gestion de credenciales | Parameter Store (si costo es critico) |
| **CloudWatch** | Logs + Metrics | Prometheus + Grafana (self-hosted) |
| **ALB** | Load balancer | Nginx en EC2 (no recomendado) |
| **IAM** | Permisos y roles | — |

**Costo estimado Phase 1:**

| Servicio | Costo mensual estimado |
|---|---|
| ECS Fargate (t2.micro equivalent) | $15-30 |
| RDS PostgreSQL (db.t3.micro) | $15-30 |
| Secrets Manager ($0.40/secret × 10 secrets) | $4 |
| CloudWatch Logs (5GB/month) | $8 |
| ALB (horas + LCUs) | $15-25 |
| **Total estimado** | **$57-97/mes** |

### CI/CD — GitHub Actions

Se adopta **GitHub Actions** como pipeline de CI/CD (simple, integrado con repositorio):

```
┌─────────────────────────────────────────────────────────────────┐
│                    GitHub Actions Pipeline                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  push / main    → build + test + lint + security scan          │
│  pull_request   → build + test + lint                          │
│  release/*      → build + test + deploy to staging              │
│  tag v*         → build + test + deploy to prod                 │
│                                                                 │
│  ┌─────────┐  ┌─────────┐  ┌──────────┐  ┌───────────┐        │
│  │  Build  →│  │  Test  →│  │  Package →│  │  Deploy  →│        │
│  │  Maven  │  │  Unit   │  │  Docker  │  │  ECS/RDS  │        │
│  └─────────┘  │  TC     │  │  Push    │  │  (manual) │        │
│               └─────────┘  └──────────┘  └───────────┘        │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

#### GitHub Actions Workflow

```yaml
name: Build and Test

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_DB: testdb
          POSTGRES_USER: test
          POSTGRES_PASSWORD: test
        ports:
          - 5432:5432
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven

      - name: Build with Maven
        run: mvn clean verify -B

      - name: Run Tests
        run: mvn test -B
        env:
          SPRING_PROFILES_ACTIVE: test
          DB_HOST: localhost
          DB_PORT: 5432

      - name: Upload coverage report
        uses: codecov/codecov-action@v3
        with:
          token: ${{ secrets.CODECOV_TOKEN }}

      - name: Build Docker image
        if: github.ref == 'refs/heads/main'
        run: docker build -t openstack-key-rotation:${{ github.sha }} .

      - name: Deploy to Staging
        if: github.ref == 'refs/heads/main' && github.event_name == 'push'
        run: |
          # Deploy to ECS staging
          aws ecs update-service --cluster staging --service app --force-new-deployment
        env:
          AWS_ACCESS_KEY_ID: ${{ secrets.AWS_ACCESS_KEY_ID }}
          AWS_SECRET_ACCESS_KEY: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          AWS_REGION: us-east-1
```

#### Security Scans en CI/CD

```yaml
  security:
    needs: build
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Run Trivy vulnerability scanner
        uses: aquasecurity/trivy-action@master
        with:
          scan-type: 'fs'
          scan-ref: '.'
          format: 'table'
          exit-code: '1'  # Fail on HIGH+ vulnerabilities

      - name: Run OWASP Dependency-Check
        uses: dependency-check/Dependency-Check_Action@main
        with:
          project: 'openstack-key-rotation'
          format: 'HTML'
```

### Health Checks

Se usa **Spring Actuator `/actuator/health`** con readiness y liveness probes:

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      probes:
        enabled: true  # Actuator mapea /actuator/health/readiness y /actuator/health/liveness
      show-details: when-authorized
      groups:
        readiness:
          include: db,redis,caffeine
        liveness:
          include: livenessState
```

#### Docker Healthcheck

```yaml
healthcheck:
  test: ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health/readiness || exit 1"]
  interval: 15s
  timeout: 5s
  retries: 3
  start_period: 60s
```

#### Kubernetes-style Probes (para ECS/Fargate)

```yaml
# ECS Health Check (define en task definition)
HealthCheck:
  Command: ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health/readiness || exit 1"]
  Interval: 30
  Timeout: 5
  Retries: 3
  StartPeriod: 60
```

### Deployment Strategy

Se adopta **blue-green deployment** para minimizar downtime:

```
  Blue (current)     Green (new)
  ┌──────────┐       ┌──────────┐
  │  v1.0.0  │       │  v1.1.0  │
  │  ECS     │       │  ECS     │
  │  Task    │       │  Task    │
  └──────────┘       └──────────┘
        │                  │
        └──────┬───────────┘
               │
        ┌──────▼───────┐
        │   ALB / LB   │
        └──────────────┘

  1. Deploy v1.1.0 a Green (sin trafico)
  2. Health check en Green → OK
  3. Cambiar ALB target group de Blue a Green
  4. Monitorear errores en Green
  5. Si hay problemas → rollback a Blue (cambiar ALB de vuelta)
  6. Terminar Blue (v1.0.0)
```

### Environment Variables por Entorno

| Variable | Dev | Staging | Prod |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | `staging` | `prod` |
| `DB_HOST` | `localhost` (Docker) | `rds-staging.internal` | `rds-prod.internal` |
| `DB_NAME` | `key_rotation` | `key_rotation` | `key_rotation` |
| `AWS_REGION` | `us-east-1` | `us-east-1` | `us-east-1` |
| `WEBHOOK_SECRET` | `dev-secret-CHANGE-ME` | (Secrets Manager) | (Secrets Manager) |
| `ALLOWED_ORIGINS` | `*` | `https://staging.example.com` | `https://app.example.com` |
| `LOGGING_LEVEL_ROOT` | `DEBUG` | `INFO` | `WARN` |

---

## Diagrama Arquitectonico Propuesto (borrador)

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Spring Boot Application                      │
│              (Java 21 + Spring Boot 3.3)                            │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                    External Interfaces                       │   │
│  ├──────────────────────────────────────────────────────────────┤   │
│  │                                                              │   │
│  │  POST /api/alerts        /api/v1/admin/rules        /actuator│   │
│  │  Webhook Controller      Config API             Health+Metrics│   │
│  │                                                              │   │
│  └──────────────────────────────┬───────────────────────────────┘   │
│                                 │                                   │
│  ┌──────────────────────────────▼───────────────────────────────┐   │
│  │                     Alert Ingestion Pipeline                 │   │
│  │                                                              │   │
│  │  WebhookController → SignatureValidator → IPValidator       │   │
│  │       → AdapterRegistry → GitGuardianAdapter                  │   │
│  │       → EventDedup (Caffeine) → SecretDedup (Caffeine)      │   │
│  │       → WorkerPool → GenericAlertModel                        │   │
│  │                                                              │   │
│  └──────────────────────────────┬───────────────────────────────┘   │
│                                 │                                   │
│  ┌──────────────────────────────▼───────────────────────────────┐   │
│  │                    Verification Engine                       │   │
│  │                                                              │   │
│  │  AlertProcessor → CredentialVerifier                        │   │
│  │       → ProviderAdapter (AWS STS) → PermissionEnum            │   │
│  │       → BlastRadiusCalculator → VerificationResult            │   │
│  │                                                              │   │
│  └──────────────────────────────┬───────────────────────────────┘   │
│                                 │                                   │
│  ┌──────────────────────────────▼───────────────────────────────┐   │
│  │                    Decision Engine                           │   │
│  │                                                              │   │
│  │  PlaybookManager (YAML) + DroolsKieService                   │   │
│  │       → severity = max(playbook_floor, drools_result)        │   │
│  │       → RuleEngine (Drools) → DecisionOutput                  │   │
│  │                                                              │   │
│  └──────────────────────────────┬───────────────────────────────┘   │
│                                 │                                   │
│  ┌──────────────────────────────▼───────────────────────────────┐   │
│  │                   Action Executor                            │   │
│  │                                                              │   │
│  │  RotationStateMachine → AwsRotationService                   │   │
│  │       → UpdateAccessKey → Wait(IAM propagation) → CreateKey │   │
│  │       → NotificationDispatcher (Strategy Pattern)            │   │
│  │                                                              │   │
│  └──────────────────────────────┬───────────────────────────────┘   │
│                                 │                                   │
│  ┌──────────────────────────────▼───────────────────────────────┐   │
│  │                 Cross-Cutting Modules                         │   │
│  ├──────────────────────────────────────────────────────────────┤   │
│  │                                                              │   │
│  │  LoggingModule (Logback + JSON + MDC)                        │   │
│  │  AuditService (JPA → audit_events table)                    │   │
│  │  MetricsModule (Micrometer + Actuator)                       │   │
│  │  SecretVault (JPA encrypted / Vault / AWS Secrets)           │   │
│  │  RuleRepository (JPA → rules table with .drl content)        │   │
│  │                                                              │   │
│  └──────────────────────────────┬───────────────────────────────┘   │
│                                 │                                   │
│  ┌──────────────────────────────▼───────────────────────────────┐   │
│  │                      Persistence Layer                        │   │
│  ├──────────────────────────────────────────────────────────────┤   │
│  │                                                              │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐   │   │
│  │  │   PostgreSQL  │  │  Caffeine    │  │  AWS SDK (STS)   │   │   │
│  │  │  (Primary DB) │  │  (In-memory) │  │  (Provider API)  │   │   │
│  │  └──────────────┘  └──────────────┘  └──────────────────┘   │   │
│  │                                                              │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Prioridad de Decisiones

Las decisiones se pueden agrupar en bloques:

1. ~~**Bloque A — Fundacional**~~ ✅ ADOPTADO:
    - ✅ Build tool: Maven 3.9.x
    - ✅ Java: 21 LTS
    - ✅ Spring Boot: 3.3.x
    - ✅ DB: PostgreSQL 16

2. ~~**Bloque B — Project Structure**~~ ✅ ADOPTADO:
    - ✅ Maven multi-module con parent POM
    - ✅ DAG de dependencias (4 dominios + shared)
    - ✅ Contract layer en `shared/spi/` con interfaces versionadas
    - ✅ Package naming: `com.company.rotations.{modulo}.{subdomain}`
    - ✅ Enforcement de build (Maven orden topologico, limites de import)

3. ~~**Bloque C — Config & Patterns**~~ ✅ ADOPTADO:
    - ✅ Global error handling: @ControllerAdvice + @ExceptionHandler
    - ✅ API versioning: /api/v1/ URL prefix
    - ✅ Security: HMAC-SHA256 webhook signing + API key admin (Spring Security disabled en Phase 1)
    - ✅ CORS: configuracion estricta (origenes explicitos)
    - ✅ Config management: application.yml + profiles (dev/staging/prod)
    - ✅ Exception types: jerarquia personalizada (BusinessException, TechnicalException, SystemException)
    - ✅ Request validation: Bean Validation (@Valid, @NotBlank, @NotNull, custom constraints)

4. ~~**Bloque D — Testing**~~ ✅ ADOPTADO:
    - ✅ Framework: JUnit 5 + Mockito + AssertJ + Spring Boot Test
    - ✅ Integration tests: @SpringBootTest + Testcontainers (Postgres real)
    - ✅ Test profiles: test (TC Postgres), dev (H2), staging/prod (Postgres real)
    - ✅ Web tests: MockMvc con verificacion de signatures HMAC
    - ✅ Drools testing: KieContainer + KieFileSystem para reglas dinamicas
    - ✅ Coverage target: 70% general, 80%+ dominio, JaCoCo en CI/CD

5. ~~**Bloque E — Vault / Secrets**~~ ✅ ADOPTADO:
    - ✅ Estrategia hibrida: AWS Secrets Manager + PostgreSQL encrypted columns
    - ✅ AWS SDK v2 SecretsManagerClient para acceso a credenciales
    - ✅ AES-256 cifrado en DB para credenciales de alerts
    - ✅ Rotation automatica via AWS Secrets Manager Lambda
    - ✅ Security: redaccion en logs, IAM role minimo, char[] para claves

6. ~~**Bloque F — Deployment**~~ ✅ ADOPTADO:
    - ✅ Docker: single container + docker-compose (infra local)
    - ✅ CI/CD: GitHub Actions (build, test, security scan)
    - ✅ Health checks: Spring Actuator readiness/liveness probes
    - ✅ Deployment: blue-green strategy (para futura escala en ECS)
    - ✅ Costo POC: **$0** (Docker Compose local + GitHub Actions gratis)

---

## Estrategia de Despliegue por Fases

### Fase 1 — POC (Proof of Concept) — **Costo: $0**

Objetivo: validar concepto sin inversion. Entorno remota, modificable, sin credenciales AWS.

| Componente | Decision | Por que |
|------------|----------|---------|
| **Deploy** | Docker Compose en VPS economico o laptop | Sin costos, infra completa (DB + app) |
| **Base de datos** | PostgreSQL 16 via Docker | Misma version que production, zero costo |
| **Secrets** | `.env` + env vars | Simple, sin rotation (no hay clientes reales) |
| **CI/CD** | GitHub Actions (2000 min/mes gratis) | Build + test + security scans |
| **Ingress** | Caddy o nginx reverse proxy | HTTPS automatico, zero costo |
| **Monitoring** | Logback + Actuator local | Sin CloudWatch |
| **Hosting VPS** | Option A: Laptop/local → **$0** | Solo para desarrollo |
| | Option B: Hetzner $4/mo | Si se necesita acceso remoto 24/7 |
| | Option C: Oracle Free Tier ARM → **$0** | 2 instancias + 200 GB + 10 TB egress |

**Docker Compose POC:**
```yaml
# docker-compose.yml
version: '3.9'
services:
  app:
    build: .
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy
    env_file: .env
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health/readiness || exit 1"]
      interval: 15s
      timeout: 5s
      retries: 3
      start_period: 60s
    restart: unless-stopped

  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: key_rotation
      POSTGRES_USER: ${DB_USER:-app}
      POSTGRES_PASSWORD: ${DB_PASSWORD:-change-me}
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U app"]
      interval: 10s
      timeout: 5s
      retries: 5

  caddy:  # reverse proxy con HTTPS automatico
    image: caddy:latest
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile
      - caddy_data:/data
    depends_on:
      - app

volumes:
  postgres_data:
  caddy_data:
```

### Fase 2 — Validacion con Primer Cliente — **Costo: $4-10/mes**

Una vez que hay un cliente real y credenciales AWS disponibles:

| Cambio | Por que |
|--------|---------|
| Migrar VPS → **AWS Free Tier** (12 meses gratis) | ECS Fargate + RDS dentro de limite gratis |
| Activar **AWS Secrets Manager** | Rotation automatica, cumplimiento |
| Habilitar **CloudWatch** | Monitoring profesional |
| CI/CD → deploy automatico a staging | Blue-green con ECS + ALB |

**Costo mensual en Free Tier: $0** (dentro de limites de 12 meses)

### Fase 3 — Produccion Escalable — **Costo: $57-97/mes**

Cuando hay multiples clientes y traffic real:

| Componente | Costo |
|------------|-------|
| ECS Fargate (2-3 servicios) | $35-65 |
| RDS PostgreSQL | $15-25 |
| ALB | $16-22 |
| Secrets Manager | $0.40/secreto |
| CloudWatch | $5-10 |

### Decision de Migracion

```
Fase 1 (POC)              Fase 2 (Cliente)            Fase 3 (Escalado)
┌──────────────┐        ┌──────────────────┐        ┌──────────────────┐
│ Docker Compose│   →   │ AWS Free Tier    │   →   │ AWS ECS + RDS    │
│ .env secrets  │       │ Secrets Manager  │       │ Blue-Green       │
│ Actuator local│       │ CloudWatch       │       │ Multi-service    │
│ Caddy/nginx   │       │ GitHub Actions   │       │ Auto-scaling     │
└──────────────┘        └──────────────────┘        └──────────────────┘
   $0/mes                 $0-10/mes                  $57-97/mes
   Sin AWS                Con AWS                    Full AWS
```

**La arquitectura esta preparada para migrar sin reescribir:**
- Los modules son independientes (Docker single container funciona igual)
- Las interfaces SPI permiten cambiar proveedores (AWS SDK se reemplaza con mocks en POC)
- El parent POM y estructura Maven son agnosticas de deployment
- Solo se requiere cambiar `application.yml` por profile

---

## Proximo Paso

La arquitectura esta definida y la estrategia de despliegue es pragmatica:

**Fase 1 (POC) — $0:** Docker Compose local con PostgreSQL real + GitHub Actions para CI.

**Siguiente accion:** Comenzar implementacion del Bloque B (Maven multi-module).

---

*(Documento generado para discusion — punto 6 de nextSesion.txt)*
