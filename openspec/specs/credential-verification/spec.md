## Purpose

TBD

## Requirements

### Requirement: Credential verification engine
The system SHALL verify the current state of cloud credentials (AWS access keys, IAM users, RDS credentials) upon receiving an alert.

#### Scenario: AWS access key verification
- **WHEN** an alert reports an exposed AWS access key
- **THEN** the system calls AWS STS `GetCallerIdentity` to verify the key is active and returns a `VerificationResult`

#### Scenario: Expired credential verification
- **WHEN** the verification provider returns that a credential is expired
- **THEN** the system returns a `VerificationResult` with `verified = false` and reason `"credential already expired"`

#### Scenario: Verification provider failure
- **WHEN** the verification provider is unreachable or returns an error
- **THEN** the system returns a `VerificationResult` with `verified = false` and reason `"provider unavailable"`

### Requirement: Blast radius calculator
The system SHALL calculate the blast radius (scope of impact) of an exposed credential based on the attached policies and permissions.

#### Scenario: High blast radius calculation
- **WHEN** an AWS access key has `AdministratorAccess` attached
- **THEN** the blast radius calculator returns `"critical"` severity scope

#### Scenario: Low blast radius calculation
- **WHEN** an AWS access key has only `ReadOnlyAccess` attached
- **THEN** the blast radius calculator returns `"low"` severity scope

### Requirement: Configurable severity rules per tenant
The system SHALL apply per-tenant severity rules to adjust the severity level based on business context.

#### Scenario: Tenant-specific severity floor
- **WHEN** a tenant has a configured severity floor of `"high"`
- **THEN** the final severity after verification is `max(calculated_severity, "high")`

#### Scenario: No tenant-specific rules
- **WHEN** a tenant has no custom severity rules configured
- **THEN** the system uses the default severity from the verification result

### Requirement: Verification provider SPI
The system SHALL use the `VerificationProvider` SPI interface to allow pluggable verification backends.

#### Scenario: AWS STS provider registration
- **WHEN** the application starts with `provider.type = aws`
- **THEN** the `AwsStsVerificationProvider` is registered as the active `VerificationProvider`

#### Scenario: Provider swap via SPI
- **WHEN** a new implementation of `VerificationProvider` is provided at runtime
- **THEN** the system uses the new provider without code changes to dependent modules
