# Credential Rotation Process

This document describes the credential rotation process implemented in the `action-executor` module, including the dual-write strategy, verification step, and rollback procedure.

---

## Overview

The rotation process transforms an exposed credential into a new valid credential with zero downtime. It uses a **dual-write** approach: the new credential is written to all storage backends and verified before the old credential is archived.

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Credential Rotation Process                      │
│                                                                     │
│  1. Generate New Credential                              ┌──────┐  │
│         │                                              │ AWS  │  │
│         ▼                                              │ IAM  │  │
│  2. Dual-Write to Storage                              └──────┘  │
│     ├─ AWS Secrets Manager (prod/staging)                     │  │
│     └─ PostgreSQL encrypted column (dev/test/backup)          │  │
│         │                                                     │  │
│         ▼                                                     │  │
│  3. Verify New Credential                                     │  │
│     ├─ AWS STS GetCallerIdentity ──▶ Valid?                   │  │
│     │   ├─ YES → Continue                                     │  │
│     │   └─ NO  → Rollback → FAIL                              │  │
│         │                                                     │  │
│         ▼                                                     │  │
│  4. Update rotated_at + Archive Old Credential                │  │
│         │                                                     │  │
│         ▼                                                     │  │
│  5. SUCCESS ──▶ Notify Channels                               │  │
└─────────────────────────────────────────────────────────────────────┘
```

---

## State Machine

Rotation actions follow a strict state machine:

```
                    ┌──────────────┐
                    │   PENDING    │
                    └──────┬───────┘
                           │ rotate() triggered
                           ▼
                    ┌──────────────┐
                    │  ROTATING    │◄───────────┐
                    └──────┬───────┘            │
                           │                    │ retry (attempts < 3)
                           ▼                    │
                    ┌──────────────┐            │
              ┌────┤   SUCCESS    │            │
              │    └──────────────┘            │
              │                                 │
              │    ┌──────────────┐            │
              └────┤     FAIL     │────────────┘
              │    └──────┬───────┘            │
              │           │                    │
              │           │ attempts >= 3      │
              │           ▼                    │
              │    ┌──────────────┐            │
              └────┤  ESCALATE    │────────────┘
                   └──────────────┘
```

### States

| State | Description | Next Transitions |
|-------|-------------|------------------|
| `PENDING` | Rotation requested, not yet started | `ROTATING` |
| `ROTATING` | Rotation in progress | `SUCCESS`, `FAIL` |
| `SUCCESS` | New credential verified and active | (terminal) |
| `FAIL` | Rotation failed (retryable) | `ROTATING` (up to 3 attempts) |
| `ESCALATE` | Rotation failed after max retries | (terminal, notification sent) |

---

## Detailed Rotation Steps

### Step 1: Generate New Credential

For **AWS Access Key rotation**:

```java
// 1. Deactivate old key (read-only: new key stays active during transition)
iamClient.deactivateAccessKey(
    new DeactivateAccessKeyRequest()
        .withAccessKeyId(oldKeyId)
        .withUserName(username)
);

// 2. Wait for deactivation to propagate (~10 seconds)
Thread.sleep(10000);

// 3. Create new key
CreateAccessKeyResponse newKey = iamClient.createAccessKey(
    new CreateAccessKeyRequest().withUserName(username)
);
```

For **IAM User rotation**:

```java
// 1. Deactivate MFA devices
iamClient.deactivateMFADevice(
    new DeactivateMFADeviceRequest()
        .withUserName(username)
        .withSerialNumber(mfaSerial)
        .withAuthCode1(mfaCode1)
        .withAuthCode2(mfaCode2)
);

// 2. Delete old access key
iamClient.deleteAccessKey(
    new DeleteAccessKeyRequest()
        .withUserName(username)
        .withAccessKeyId(oldKeyId)
);

// 3. Create new key
CreateAccessKeyResponse newKey = iamClient.createAccessKey(
    new CreateAccessKeyRequest().withUserName(username)
);
```

### Step 2: Dual-Write to Storage

The new credentials are written to **both** storage backends simultaneously:

```java
void dualWrite(String tenantId, Map<String, String> credentials) {
    // Primary: AWS Secrets Manager (prod/staging)
    try {
        PutSecretValueRequest request = new PutSecretValueRequest()
            .withSecretId("/clients/" + tenantId + "/aws-access-key")
            .withSecretString(JsonUtil.toJson(credentials));
        secretsManagerClient.putSecretValue(request);
    } catch (Exception e) {
        logger.warn("AWS SM write failed, falling back to DB");
    }

    // Backup: PostgreSQL encrypted column (all profiles)
    ClientCredential credential = new ClientCredential();
    credential.setTenantId(tenantId);
    credential.setAccessKeyEncrypted(aes256Encrypt(credentials.get("accessKeyId")));
    credential.setSecretKeyEncrypted(aes256Encrypt(credentials.get("secretKey")));
    credential.setVaultRef("/clients/" + tenantId + "/aws-access-key");
    credential.setRotatedAt(Instant.now());
    credentialRepository.save(credential);
}
```

### Step 3: Verify New Credential

Before marking rotation as successful, the new credential must be verified:

```java
VerificationResult result = verificationProvider.verify(
    "AWS_ACCESS_KEY",
    credentials,  // new credentials from step 1
    tenantId
);

if (result.isVerified()) {
    stateMachine.transitionToSuccess();
} else {
    stateMachine.transitionToFailure();
    // Rollback triggered automatically
}
```

Verification uses AWS STS `GetCallerIdentity` — if the credential is valid, the call succeeds; if invalid/expired, it throws.

### Step 4: Archive Old Credential

After successful verification:

```java
void archiveOldCredentials(String tenantId, String oldKeyId) {
    // Update rotated_at timestamp
    ClientCredential credential = credentialRepository.findByTenantId(tenantId);
    credential.setRotatedAt(Instant.now());
    credentialRepository.save(credential);

    // Log rotation in audit trail
    auditEventService.log(AuditEventType.CREDENTIAL_ACCESSED, tenantId, 
        null, "Old credential archived after rotation");
}
```

### Step 5: Send Notifications

On success or failure, notifications are sent via all configured channels:

```java
notificationDispatcher.dispatch(
    "rotation." + state,  // "rotation.success" or "rotation.failed"
    Map.of(
        "alertId", alertId.toString(),
        "tenantId", tenantId,
        "credentialType", credentialType.name(),
        "attempts", String.valueOf(attempts),
        "severity", severity.name()
    )
);
```

---

## Rollback Procedure

If verification fails in Step 3, the system performs an automatic rollback:

```java
void rollback(String tenantId, String newKeyId) {
    // 1. Delete the new (unverified) credential from AWS IAM
    iamClient.deleteAccessKey(
        new DeleteAccessKeyRequest()
            .withUserName(username)
            .withAccessKeyId(newKeyId)
    );

    // 2. Remove from Secrets Manager
    secretsManagerClient.deleteSecret(
        new DeleteSecretRequest()
            .withSecretId("/clients/" + tenantId + "/aws-access-key")
            .withForceDeleteWithoutRecovery(true)
    );

    // 3. Update rotation state
    stateMachine.transitionToFailure();

    // 4. Log rollback in audit trail
    auditEventService.log(AuditEventType.ROTATION_FAILED, tenantId,
        alertId, "Rollback: new credential verification failed");

    // 5. If max retries exceeded → escalate
    if (stateMachine.getAttempts() >= stateMachine.getMaxRetries()) {
        escalationService.trigger(AlertEventType.ROTATION_FAILED, tenantId, alertId);
    }
}
```

---

## Rotation Policy

### Frequency

| Policy | Default | Configurable |
|--------|---------|--------------|
| Rotation deadline | 90 days | Yes, per tenant |
| Max retry attempts | 3 | Yes, globally |
| Rotation timeout | 5 minutes | Yes, per credential type |
| Alert trigger | 75 days before deadline | Yes, per tenant |

### Tracking

The `Credential` entity tracks rotation policy:

```java
@Entity
@Table(name = "credentials")
public class Credential {
    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @Column(name = "rotation_deadline_days")
    private Integer rotationDeadlineDays = 90;

    public boolean isRotationRequired() {
        return rotatedAt.plusDays(rotationDeadlineDays).isBefore(Instant.now());
    }

    public boolean isRotationOverdue() {
        return rotatedAt.plusDays(rotationDeadlineDays).isBefore(Instant.now().minus(Duration.ofDays(7)));
    }
}
```

---

## Error Handling

| Error | Response | Retry | Escalation |
|-------|----------|-------|------------|
| AWS IAM rate limit | FAIL | Yes (up to max retries) | After max retries |
| STS verification fails | FAIL | Yes (up to max retries) | After max retries |
| Secrets Manager unavailable | FAIL | No | Immediate |
| Network timeout | FAIL | Yes (up to max retries) | After max retries |
| Invalid credential format | FAIL | No | Immediate |

---

## Testing

### Unit Tests

| Test | Description |
|------|-------------|
| `AwsRotationServiceTest` | Full rotation flow with mocked AWS clients |
| `RotationStateMachineTest` | State transitions (PENDING → ROTATING → SUCCESS/FAIL) |
| `SecretVaultServiceTest` | Dual-write, fallback, path conventions |
| `Aes256ConverterTest` | Encrypt/decrypt roundtrip |

### Integration Tests

| Test | Description |
|------|-------------|
| `RotationIntegrationTest` | End-to-end rotation with Testcontainers PostgreSQL |
| `SecretVaultIntegrationTest` | Dual-write and fallback with encrypted columns |
