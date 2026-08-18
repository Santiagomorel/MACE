# Secrets Management Strategy

This document describes the hybrid secrets management approach used by the Credential Rotation System: AWS Secrets Manager as primary storage (for prod/staging) and PostgreSQL AES-256 encrypted columns as fallback (for dev/test and backup).

---

## Overview

The system uses a **dual-layer strategy** for protecting tenant credentials:

| Layer | Primary | Backup |
|-------|---------|--------|
| **Storage** | AWS Secrets Manager | PostgreSQL encrypted columns |
| **Environments** | prod / staging | dev / test / all (backup) |
| **Encryption** | AWS managed key | AES-256 via JPA `AttributeConverter` |

### Retrieval Flow

```
1. Try AWS Secrets Manager (primary)
   ├─ Success → Return credentials
   └─ Failure → 2. Fallback to PostgreSQL (backup)
       ├─ Success → Return credentials (log fallback)
       └─ Failure → 3. Return error
```

---

## AWS Secrets Manager (Primary)

### Path Convention

Secrets are stored under standardized paths:

```
/app/rotation/{secret-type}           — System rotation secrets
/clients/{tenantId}/aws-access-key    — Tenant AWS access key
/clients/{tenantId}/aws-secret-key    — Tenant AWS secret key
/clients/{tenantId}/aws-region        — Tenant AWS region
/clients/{tenantId}/metadata          — Tenant metadata (JSON)
```

### IAM Permissions

Minimum IAM policy for Secrets Manager access:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "secretsmanager:GetSecretValue",
      "Resource": "arn:aws:secretsmanager:*:*:secret:/app/rotation/*"
    },
    {
      "Effect": "Allow",
      "Action": "secretsmanager:GetSecretValue",
      "Resource": "arn:aws:secretsmanager:*:*:secret:/clients/*"
    }
  ]
}
```

### Secret Rotation

AWS Secrets Manager supports automatic rotation with Lambda:

```
┌────────────────────────────────────────────────────┐
│  AWS Secrets Manager Rotation Schedule             │
│                                                     │
│  Every 90 days → Lambda triggers                   │
│       │                                           │
│       ▼                                           │
│  1. Create new secret version                      │
│  2. Update RDS with new credentials                │
│  3. Test new credentials                           │
│  4. Set new version as "AWSCURRENT"                │
└────────────────────────────────────────────────────┘
```

---

## PostgreSQL AES-256 (Fallback/Backup)

### JPA Attribute Converter

Encrypts/decrypts columns transparently via JPA:

```java
@Converter
public class Aes256Converter implements AttributeConverter<String, String> {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";

    private final SecretKey key;

    public Aes256Converter(@Value("${app.encryption.master-key}") String masterKey) {
        this.key = new SecretKeySpec(
            masterKey.getBytes(StandardCharsets.UTF_8), ALGORITHM
        );
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encrypted = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new EncryptionException("Failed to encrypt value", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decoded = Base64.getDecoder().decode(dbData);
            byte[] decrypted = cipher.doFinal(decoded);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new EncryptionException("Failed to decrypt value", e);
        }
    }
}
```

### Encrypted Entity

```java
@Entity
@Table(name = "client_credentials")
public class ClientCredential {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private String tenantId;

    @Column(name = "provider", nullable = false)
    private String provider;

    @Column(name = "access_key_encrypted", nullable = false)
    @Convert(converter = Aes256Converter.class)
    private String accessKeyEncrypted;

    @Column(name = "secret_key_encrypted", nullable = false)
    @Convert(converter = Aes256Converter.class)
    private String secretKeyEncrypted;

    @Column(name = "vault_ref")
    private String vaultRef;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @Override
    public String toString() {
        // Mask encrypted values
        return "ClientCredential{" +
            "tenantId='" + tenantId + '\'' +
            ", accessKeyEncrypted=****" +
            ", secretKeyEncrypted=****" +
            ", vaultRef='" + vaultRef + '\'' +
            ", createdAt=" + createdAt +
            '}';
    }
}
```

### Table Schema

```sql
CREATE TABLE client_credentials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(255) NOT NULL UNIQUE,
    provider VARCHAR(50) NOT NULL,          -- AWS, AZURE, GCP
    access_key_encrypted VARCHAR(500) NOT NULL,
    secret_key_encrypted VARCHAR(500) NOT NULL,
    region VARCHAR(50),
    additional_config JSONB,
    vault_ref VARCHAR(500),                 -- Path in AWS Secrets Manager
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    rotated_at TIMESTAMP
);

CREATE INDEX idx_client_credentials_tenant_id ON client_credentials(tenant_id);
CREATE INDEX idx_client_credentials_provider ON client_credentials(provider);
```

---

## SecretVaultService

Central service that abstracts both storage backends:

```java
@Service
public class SecretVaultService {

    private final SecretsManagerClient secretsManagerClient;
    private final CredentialRepository credentialRepository;
    private final Aes256Converter aes256Converter;
    private final String profile;

    public Optional<StoredSecret> getSecret(String secretPath) {
        // Primary: Try AWS Secrets Manager
        if ("prod".equals(profile) || "staging".equals(profile)) {
            try {
                GetSecretValueRequest request = GetSecretValueRequest.builder()
                    .secretId(secretPath)
                    .build();
                GetSecretValueResponse response = secretsManagerClient.getSecretValue(request);
                if (response.secretString() != null) {
                    return Optional.of(StoredSecret.fromJson(response.secretString()));
                }
            } catch (Exception e) {
                log.warn("AWS Secrets Manager unavailable for path {}, falling back to DB", secretPath);
            }
        }

        // Fallback: PostgreSQL encrypted columns
        return credentialRepository.findByTenantId(extractTenantFromPath(secretPath))
            .map(credential -> {
                String accessKey = credential.getAccessKeyEncrypted(); // Auto-decrypt via converter
                String secretKey = credential.getSecretKeyEncrypted(); // Auto-decrypt via converter
                return new StoredSecret(accessKey, secretKey, secretPath);
            });
    }

    public void storeSecret(String secretPath, StoredSecret secret) {
        // Primary: Write to AWS Secrets Manager
        if ("prod".equals(profile) || "staging".equals(profile)) {
            PutSecretValueRequest request = PutSecretValueRequest.builder()
                .secretId(secretPath)
                .secretString(secret.toJson())
                .build();
            secretsManagerClient.putSecretValue(request);
        }

        // Always: Save encrypted backup in PostgreSQL
        ClientCredential credential = new ClientCredential();
        credential.setTenantId(extractTenantFromPath(secretPath));
        credential.setAccessKeyEncrypted(secret.getAccessKey()); // Encrypted by converter
        credential.setSecretKeyEncrypted(secret.getSecretKey()); // Encrypted by converter
        credential.setVaultRef(secretPath);
        credentialRepository.save(credential);
    }
}
```

---

## Secret Redaction in Logs

Logback custom converter to prevent secret leakage:

```java
public class SecretRedactingConverter extends Converter<ILoggingEvent> {

    private static final Pattern AWS_KEY_PATTERN = Pattern.compile("AKIA[A-Z0-9]{16}");
    private static final Pattern AWS_SECRET_PATTERN = Pattern.compile("([A-Za-z0-9/+=]{40})");

    @Override
    public String convert(ILoggingEvent event) {
        String message = event.getMessage();
        if (message == null) return null;

        // Redact AWS access key IDs
        message = AWS_KEY_PATTERN.matcher(message).replaceAll("****REDACTED****");

        // Redact AWS secret keys (40-char alphanumeric strings after known prefixes)
        message = message.replaceAll("(secretKey[s]?)\\s*[:=]\\s*\\S+", "$1 = ****REDACTED****");

        return message;
    }
}
```

### Logback Configuration

```xml
<configuration>
    <conversionRule conversionWord="redact" converterClass="com.company.rotations.logging.SecretRedactingConverter" />

    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <customFields>{"service":"rotation-system"}</customFields>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="JSON" />
    </root>
</configuration>
```

---

## Encryption Key Management

### POC (Development)

Master key loaded from environment variable:

```bash
export ENCRYPTION_MASTER_KEY=$(openssl rand -hex 32)
```

### Production

Master key managed by AWS KMS:

```yaml
app:
  encryption:
    master-key-provider: aws-kms
    kms-key-id: ${KMS_KEY_ID}
```

KMS decrypts the master key at startup; the master key never leaves AWS.

---

## Security Best Practices

1. **Never commit secrets** to git — use `.gitignore` for `.env.*` files
2. **Use environment variables** for configuration (not hardcoded values)
3. **Rotate secrets** every 90 days minimum
4. **Redact secrets in logs** using Logback converters
5. **Mask encrypted values** in `toString()` methods
6. **Use least-privilege IAM** for Secrets Manager access
7. **Enable KMS key rotation** for AWS-managed keys
8. **Backup encrypted columns** separately from plaintext data
