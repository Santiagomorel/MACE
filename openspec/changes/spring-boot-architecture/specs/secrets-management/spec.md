# Secrets Management

## ADDED Requirements

### Requirement: Hybrid secret storage strategy
The system SHALL use a hybrid secrets strategy combining AWS Secrets Manager (primary for production) and PostgreSQL AES-256 encrypted columns (backup/fallback for alert payloads and encrypted storage).

#### Scenario: Production mode uses AWS Secrets Manager
- **WHEN** the application runs in `prod` or `staging` profile
- **THEN** `SecretVaultService` uses `SecretsManagerClient` (AWS SDK v2) to read/write secrets
- **AND** secret paths follow the convention: `/app/rotation/{secret-path}`

#### Scenario: POC mode uses encrypted fallback
- **WHEN** the application runs in `dev` or `test` profile
- **THEN** `SecretVaultService` falls back to PostgreSQL encrypted columns for storage
- **AND** the encryption master key is provided via environment variable `ENCRYPTION_MASTER_KEY`

#### Scenario: Secret path convention
- **WHEN** a secret is stored or retrieved
- **THEN** it uses the path prefix `/app/rotation/` followed by the specific path (e.g., `/app/rotation/db-password`, `/app/rotation/webhook-secret`, `/app/rotation/aws-creds/{tenantId}`)

### Requirement: Tenant credential double-layer protection
The system SHALL protect admin credentials of each client (read-only credentials used to verify cloud infrastructure) with a double-layer protection strategy.

#### Scenario: Primary layer — AWS Secrets Manager
- **WHEN** tenant credentials are stored in production
- **THEN** they are stored in AWS Secrets Manager at `/clients/{tenantId}/aws-access-key`, `/clients/{tenantId}/aws-secret-key`, `/clients/{tenantId}/aws-region`, and `/clients/{tenantId}/metadata`
- **AND** IAM role grants minimal permissions (`secretsmanager:GetSecretValue` only)

#### Scenario: Backup layer — PostgreSQL encrypted
- **WHEN** tenant credentials are stored
- **THEN** an encrypted backup is saved in the `client_credentials` table with AES-256 encrypted columns (`access_key_encrypted`, `secret_key_encrypted`)
- **AND** the table includes `vault_ref` (path in Secrets Manager), `created_at`, `updated_at`, and `rotated_at` audit fields

#### Scenario: Credential retrieval
- **WHEN** the system needs tenant credentials for verification
- **THEN** it reads from AWS Secrets Manager first (primary layer)
- **AND** falls back to PostgreSQL encrypted backup only if Secrets Manager is unavailable

### Requirement: AES-256 column encryption
The system SHALL encrypt sensitive columns in PostgreSQL using AES-256 encryption via a JPA `AttributeConverter`.

#### Scenario: Encryption on persist
- **WHEN** an entity with an encrypted field is persisted
- **THEN** the `Aes256Converter` encrypts the value before writing to the database
- **AND** the encryption key is loaded from the application configuration at startup

#### Scenario: Decryption on read
- **WHEN** an entity with an encrypted field is loaded from the database
- **THEN** the `Aes256Converter` decrypts the value after reading from the database
- **AND** the decrypted value is available as a normal Java field in the entity

#### Scenario: Encrypted columns never exposed in logs
- **WHEN** an entity with encrypted fields is logged
- **THEN** the encrypted value is logged (not the plaintext secret)
- **AND** the `toString()` method of encrypted entities masks the encrypted value (e.g., `****`)

### Requirement: Secret rotation policy
The system SHALL enforce a credential rotation policy for tenant credentials.

#### Scenario: Rotation frequency
- **WHEN** a tenant credential is stored
- **THEN** the system tracks the `rotated_at` timestamp
- **AND** alerts are triggered when credentials approach the rotation deadline (90 days default, configurable per tenant)

#### Scenario: Rotation process
- **WHEN** a credential rotation is triggered (scheduled or manual)
- **THEN** the system performs a dual-write: generate new credentials → store in Secrets Manager → save encrypted backup in DB → verify new credentials → archive old version
- **AND** if verification fails, the rotation is rolled back and an escalation notification is sent

#### Scenario: Rotation failure escalation
- **WHEN** credential rotation fails after 3 retry attempts
- **THEN** the system transitions the rotation state to `ESCALATE`
- **AND** sends a notification via all configured channels (Slack, Email, Ticket)

### Requirement: Secret redaction in logs
The system SHALL redact secrets from all log output to prevent credential leakage.

#### Scenario: Logback secret redaction
- **WHEN** a log message contains a value matching a secret pattern (e.g., `AKIA` for AWS access keys)
- **THEN** the `SecretRedactingConverter` replaces the value with `***REDACTED***`
- **AND** the redaction is applied at the Logback appender level for all log outputs

#### Scenario: Error messages do not expose secrets
- **WHEN** an exception or error response is generated
- **THEN** stack traces and internal details are not included in API error responses
- **AND** error messages do not contain partial secrets (e.g., AWS key IDs are masked)

### Requirement: Database credential table schema
The system SHALL maintain a `client_credentials` table for encrypted backup of tenant credentials.

#### Scenario: Table schema
- **WHEN** the database schema is initialized
- **THEN** the `client_credentials` table is created with columns: `client_id` (UUID PK), `tenant_id` (VARCHAR unique), `provider` (ENUM: AWS, AZURE, GCP), `access_key_encrypted` (VARCHAR), `secret_key_encrypted` (VARCHAR), `region` (VARCHAR), `additional_config` (JSONB), `vault_ref` (VARCHAR), `created_at`, `updated_at`, `rotated_at`

#### Scenario: Tenant credential lookup
- **WHEN** the system needs to find a tenant's credentials by tenantId
- **THEN** it queries the `client_credentials` table by `tenant_id` column
- **AND** returns the encrypted values along with the `vault_ref` for Secrets Manager retrieval
