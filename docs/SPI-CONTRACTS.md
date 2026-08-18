# SPI Contracts

This document describes all Service Provider Interfaces (SPIs) in `shared/spi/`. Each interface decouples communication between modules. Modules import only interfaces, never concrete implementations from other modules.

---

## AlertAdapter

**Module:** `shared/shared-spi`  
**Provider:** `alert-integrator`  
**Consumer:** `verification-engine`  
**Version:** 1.0.0

Maps raw webhook payloads from external providers into the generic `GenericAlertModel`.

```java
public interface AlertAdapter {
    String VERSION = "1.0.0";

    GenericAlertModel toGenericAlert(Map<String, Object> rawPayload);
    String getProviderName();

    default String getVersion() {
        return VERSION;
    }
}
```

### Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `toGenericAlert(rawPayload)` | `GenericAlertModel` | Normalizes a provider-specific payload into the generic model |
| `getProviderName()` | `String` | Returns the provider name (e.g., `"gitguardian"`) used for adapter lookup |
| `getVersion()` | `String` | Returns the SPI version (default: `"1.0.0"`) |

### Example Implementation

```java
@Component
public class GitGuardianAdapter implements AlertAdapter {

    @Override
    public String getProviderName() {
        return "gitguardian";
    }

    @Override
    public GenericAlertModel toGenericAlert(Map<String, Object> rawPayload) {
        // Extract incident data from GitGuardian v2 API payload
        Map<String, Object> incident = (Map<String, Object>) rawPayload.get("incident");
        
        GenericAlertModel model = new GenericAlertModel();
        model.setSource("gitguardian");
        model.setSourceEventId(extractString(incident, "id"));
        model.setEventId(model.getSourceEventId());
        model.setReceivedAt(Instant.now());

        GenericAlertModel.DetectedSecret secret = new GenericAlertModel.DetectedSecret();
        secret.setType(extractString(incident, "secret_type"));
        secret.setValueHash(extractString(incident, "value_hash"));
        secret.setPattern(extractString(incident, "detector"));
        model.setDetectedSecret(secret);

        GenericAlertModel.AlertContext ctx = new GenericAlertModel.AlertContext();
        ctx.setRepository(extractString(incident, "repository"));
        ctx.setFile(extractString(incident, "file"));
        ctx.setCommit(extractString(incident, "commit"));
        model.setContext(ctx);

        model.setRawPayload(Collections.unmodifiableMap(rawPayload));
        return model;
    }
}
```

### Registration

Adapters are auto-registered via Spring `@Component` scanning. The `AdapterRegistry` discovers all `AlertAdapter` beans and maps them by `getProviderName()`.

---

## VerificationProvider

**Module:** `shared/shared-spi`  
**Provider:** `verification-engine`  
**Consumer:** `decision-engine`  
**Version:** 1.0.0

Verifies whether a credential is currently valid/active against cloud provider APIs.

```java
public interface VerificationProvider {
    String VERSION = "1.0.0";

    VerificationResult verify(String credentialType, Map<String, String> credentials, String tenantId);

    default String getVersion() {
        return VERSION;
    }
}
```

### Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `verify(credentialType, credentials, tenantId)` | `VerificationResult` | Attempts to verify credentials against the provider; returns result with `verified` flag, reason, and blast radius |
| `getVersion()` | `String` | Returns the SPI version (default: `"1.0.0"`) |

### Example Implementation

```java
@Component
public class AwsStsVerificationProvider implements VerificationProvider {

    private final StsClient stsClient;

    @Override
    public VerificationResult verify(String credentialType, 
                                      Map<String, String> credentials, 
                                      String tenantId) {
        String accessKeyId = credentials.get("accessKeyId");
        String secretKey = credentials.get("secretKey");
        
        BasicAWSCredentials awsCreds = new BasicAWSCredentials(accessKeyId, secretKey);
        
        try {
            GetCallerIdentityRequest request = new GetCallerIdentityRequest();
            GetCallerIdentityResponse response = stsClient.getCallerIdentity(request, awsCreds);
            
            // Credential is valid
            return new VerificationResult(
                null,                    // alertId (set later by caller)
                true,                    // verified
                "STS GetCallerIdentity succeeded",
                calculateBlastRadius(response.getArn()),
                null,                    // blastRadius
                AlertType.AWS_ACCESS_KEY,
                tenantId,
                "aws"
            );
        } catch (Exception e) {
            // Credential is invalid/expired
            return new VerificationResult(
                null,
                false,
                e.getMessage(),
                "NONE",                  // blast radius when credential is dead
                null,
                AlertType.AWS_ACCESS_KEY,
                tenantId,
                "aws"
            );
        }
    }
}
```

---

## DecisionEngine

**Module:** `shared/shared-spi`  
**Provider:** `decision-engine`  
**Consumer:** `action-executor`  
**Version:** N/A (internal interface)

Evaluates alert + verification data against Drools rules and playbooks to produce a decision.

```java
public interface DecisionEngine {
    DecisionResult evaluate(UUID alertId, String tenantId, String credentialType,
                            Map<String, Object> actionMatrix,
                            Map<String, Object> metadata);

    interface DecisionResult {
        Severidad getSeverity();
        String getRationale();
        String getPlaybookId();
        String getCalculatedVia();
        Map<String, Object> getComplianceTags();
        Integer getEvaluatedRuleVersion();
    }
}
```

### Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `evaluate(alertId, tenantId, credentialType, actionMatrix, metadata)` | `DecisionResult` | Evaluates the alert and returns the decision (rotate/no_action/escalate) with severity, rationale, and playbook reference |

### Example Usage

```java
@Service
public class DecisionOrchestrator {

    private final DecisionEngine decisionEngine;

    public DecisionResult decide(UUID alertId, String tenantId, 
                                  AlertType credentialType,
                                  VerificationResult verificationResult) {
        Map<String, Object> actionMatrix = new HashMap<>();
        actionMatrix.put("verified", verificationResult.isVerified());
        actionMatrix.put("severityScope", verificationResult.getSeverityScope());
        actionMatrix.put("blastRadius", verificationResult.getBlastRadius());

        Map<String, Object> metadata = Map.of(
            "tenantId", tenantId,
            "credentialType", credentialType.name(),
            "verificationReason", verificationResult.getReason()
        );

        return decisionEngine.evaluate(alertId, tenantId, 
            credentialType.name(), actionMatrix, metadata);
    }
}
```

---

## PlaybookManager

**Module:** `shared/shared-spi`  
**Provider:** `decision-engine`  
**Consumer:** `action-executor`  
**Version:** 1.0.0

Manages YAML playbooks that define rotation procedures, severity floors, and compliance tags per credential type.

```java
public interface PlaybookManager {
    String VERSION = "1.0.0";

    Playbook loadPlaybook(String credentialType);
    Playbook loadPlaybookByPlaybookId(String playbookId);
    List<String> getPlaybookSteps(String credentialType);
    List<Severidad> getSeverityFloor(String playbookId);
    boolean validatePlaybook(Playbook playbook);

    default String getVersion() {
        return VERSION;
    }

    interface Playbook {
        String getPlaybookId();
        String getVersion();
        List<String> getCredentialTypes();
        Map<String, Severidad> getSeverityFloor();
        AutoRotateConfig getAutoRotate();
        List<ActionOnExposure> getActionsOnExposure();
        List<ComplianceTag> getComplianceTags();
        List<CredentialTargeted> getCredentialsTargeted();
        Conditions getConditions();
        boolean canLowerFloor();

        interface AutoRotateConfig {
            boolean isEnabled();
            Integer getMaxWindowMins();
        }

        interface ActionOnExposure {
            String getActionType();
            String getTarget();
            int getPriorityOrder();
        }

        interface ComplianceTag {
            String getSource();
            String getControlDescription();
        }

        interface CredentialTargeted {
            String getCredentialType();
            String getDescription();
        }

        interface Conditions {
            String getProvider();
            String getDetectionSource();
        }
    }
}
```

### Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `loadPlaybook(credentialType)` | `Playbook` | Loads the playbook matching a credential type (e.g., `"AWS_ACCESS_KEY"`) |
| `loadPlaybookByPlaybookId(playbookId)` | `Playbook` | Loads a playbook by its unique ID |
| `getPlaybookSteps(credentialType)` | `List<String>` | Returns the ordered list of rotation steps |
| `getSeverityFloor(playbookId)` | `List<Severidad>` | Returns the minimum severity levels for different scenarios |
| `validatePlaybook(playbook)` | `boolean` | Validates playbook structure and required fields |

### Playbook YAML Example

```yaml
# src/main/resources/playbooks/aws-access-key-exposed.yml
playbook_id: aws-access-key-exposed
version: "1.0.0"
credential_types:
  - AKIA
severity_floor:
  s3_full_access: CRITICO
  s3_read_only: ALTO
  iam_modify: CRITICO
  ec2_instance_control: CRITICO
  cloudwatch_read: MEDIA
  nothing_active: BAJO
auto_rotate:
  enabled: true
  max_window_mins: 15
can_lower_floor: false
actions_on_exposure:
  - action_type: rotate
    target: access_key
    priority_order: 1
  - action_type: notify
    target: security_team
    priority_order: 2
```

---

## RotationService

**Module:** `shared/shared-spi`  
**Provider:** `action-executor`  
**Consumer:** External (other modules or admin APIs)  
**Version:** 1.0.0

Performs credential rotation operations (e.g., create new AWS access key, deactivate old one).

```java
public interface RotationService {
    String VERSION = "1.0.0";

    RotationAction rotate(String credentialType, Map<String, String> credentials, String tenantId);

    default String getVersion() {
        return VERSION;
    }
}
```

### Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `rotate(credentialType, credentials, tenantId)` | `RotationAction` | Performs the rotation; returns the rotation action entity tracking the operation state |

### Example Implementation

```java
@Service
public class AwsAccessKeyRotationService implements RotationService {

    private final IamClient iamClient;
    private final RotationStateMachine stateMachine;

    @Override
    public RotationAction rotate(String credentialType, 
                                  Map<String, String> credentials, 
                                  String tenantId) {
        String accessKeyId = credentials.get("accessKeyId");
        
        // Step 1: Deactivate old key
        iamClient.deactivateAccessKey(
            new DeactivateAccessKeyRequest()
                .withAccessKeyId(accessKeyId)
                .withUserName(credentials.get("userName"))
        );

        // Step 2: Create new key
        CreateAccessKeyResponse newKey = iamClient.createAccessKey(
            new CreateAccessKeyRequest().withUserName(credentials.get("userName"))
        );

        // Step 3: Dual-write to Secrets Manager + DB
        secretVaultService.storeCredentials(tenantId, Map.of(
            "accessKeyId", newKey.getAccessKey().getAccessKeyId(),
            "secretKey", newKey.getAccessKey().getSecretAccessKey()
        ));

        // Step 4: Verify new key
        VerificationResult result = verificationProvider.verify(
            "AWS_ACCESS_KEY", 
            newKey.getAccessKey(), 
            tenantId
        );

        if (result.isVerified()) {
            stateMachine.transitionToSuccess();
            return new RotationAction(...);
        } else {
            stateMachine.transitionToFailure();
            throw new RotationFailureException("New key verification failed");
        }
    }
}
```

---

## NotificationChannel

**Module:** `shared/shared-spi`  
**Provider:** `action-executor`  
**Consumer:** External  
**Version:** 1.0.0

Sends notifications via pluggable channels (Slack, Email, Ticket, SNS).

```java
public interface NotificationChannel {
    String VERSION = "1.0.0";

    void send(String message, Map<String, String> context);

    default String getVersion() {
        return VERSION;
    }
}
```

### Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `send(message, context)` | `void` | Sends a notification with message and contextual metadata (alertId, tenantId, severity, etc.) |

### Example Implementations

```java
@Component
public class SlackNotificationChannel implements NotificationChannel {
    private final Slack slack = Slack.getInstance();

    @Override
    public void send(String message, Map<String, String> context) {
        slack.chatPostMessage(
            ChatPostMessageRequest.builder()
                .channel("#security-alerts")
                .text(message)
                .blocks(List.of(
                    mrkdwnSection(context.get("alertId")),
                    mrkdwnSection(context.get("severity")),
                    mrkdwnSection(context.get("tenantId"))
                ))
                .build()
        );
    }
}

@Component
public class EmailNotificationChannel implements NotificationChannel {
    private final JavaMailSender mailSender;

    @Override
    public void send(String message, Map<String, String> context) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo("security@company.com");
        mail.setSubject("[MACE] Alert: " + context.get("severity"));
        mail.setText(message);
        mailSender.send(mail);
    }
}
```

---

## Versioning Policy

| Change Type | Version Bump | Example |
|-------------|-------------|---------|
| Breaking (method removed, signature changed) | Major | `1.0.0` → `2.0.0` |
| Backward-compatible (new default method) | Minor | `1.0.0` → `1.1.0` |
| No code changes | None | `1.0.0` stays `1.0.0` |

All SPIs default to version `1.0.0`. Breaking changes require coordinating with all consumers and updating POM versions in `shared/shared-spi`.
