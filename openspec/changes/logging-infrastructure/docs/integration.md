## Integration Guide

### For Module Authors

The logging module auto-configures itself. To use it in your module:

#### 1. Add Dependency

In your module's `pom.xml`:

```xml
<dependency>
    <groupId>com.company</groupId>
    <artifactId>logging</artifactId>
    <version>${project.version}</version>
</dependency>
```

#### 2. Inject AuditService

```java
@Service
public class MyService {
    private final AuditService auditService;

    public MyService(AuditService auditService) {
        this.auditService = auditService;
    }
}
```

#### 3. Write Audit Events

```java
// In your service method
auditService.logWebhookReceived(Map.of(
    "provider", "gitguardian",
    "url", requestUrl
));
```

#### 4. Set Phase MDC Field

Before calling audit methods, set the phase:

```java
MDC.put("phase", "my-module-phase");
try {
    auditService.logVerificationStarted(data);
} finally {
    MDC.remove("phase");
}
```

### What's Auto-Configured

- `MdcLoggingFilter` — intercepts all requests, populates MDC
- `AuditService` — audit event persistence
- `AuditPurgeService` — scheduled purge (daily at 2 AM)
- `logback-spring.xml` — JSON logging with async appenders
- Secret redaction via `SecretRedactionConverter`
- Metrics via Micrometer (`/actuator/prometheus`)

### What You Don't Need

- Manual bean registration
- Logback configuration
- MDC field management (handled by filter)
- Audit table creation (Flyway migration)
