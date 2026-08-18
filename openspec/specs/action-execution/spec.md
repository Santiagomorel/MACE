## Purpose

TBD

## Requirements

### Requirement: Rotation state machine
The system SHALL use a state machine to manage the lifecycle of credential rotation actions.

#### Scenario: Rotation state transitions
- **WHEN** a rotation action begins
- **THEN** the state machine transitions: `PENDING` → `ROTATING` → `SUCCESS` | `FAIL`

#### Scenario: Retry on failure
- **WHEN** a rotation fails (e.g., AWS API error)
- **THEN** the state machine transitions to `ROTATING` again up to 3 retry attempts

#### Scenario: Escalation after max retries
- **WHEN** a rotation fails after 3 retry attempts
- **THEN** the state machine transitions to `ESCALTAE` and triggers a notification

### Requirement: AWS rotation services
The system SHALL implement rotation services for AWS credential types: AccessKey and IAMUser.

#### Scenario: Access key rotation
- **WHEN** the decision is to rotate an AWS access key
- **THEN** the `AwsRotationService` executes: `set_key_inactive` → `wait_propagation` → `create_new_key` → `verify_new_key`

#### Scenario: IAM user credential rotation
- **WHEN** the decision is to rotate IAM user credentials
- **THEN** the `AwsRotationService` executes: `deactivate_console_password` → `delete_access_key` → `create_new_access_key` → `create_console_password` → `verify`

### Requirement: Notification dispatcher (Strategy pattern)
The system SHALL dispatch notifications through configurable channels using the `NotificationChannel` SPI interface.

#### Scenario: Slack notification
- **WHEN** a rotation completes with `status = FAIL`
- **THEN** the `NotificationDispatcher` sends a message to the configured Slack webhook URL via `SlackNotificationChannel`

#### Scenario: Email notification
- **WHEN** a rotation reaches `ESCALATE` state
- **THEN** the `NotificationDispatcher` sends an email via `EmailNotificationChannel` to the on-call team

#### Scenario: Multiple notification channels
- **WHEN** a rotation completes with `status = ESCALATE`
- **THEN** the system sends notifications to all configured channels (Slack + Email + Ticket)

### Requirement: Credential rotation with dual-write
The system SHALL perform credential rotation with a dual-write strategy to ensure zero downtime.

#### Scenario: Dual-write during rotation
- **WHEN** new credentials are generated
- **THEN** the system writes to both AWS Secrets Manager and the encrypted PostgreSQL backup before switching

#### Scenario: Old credential cleanup
- **WHEN** new credentials are verified as working
- **THEN** the system archives the old version in Secrets Manager and updates the DB `rotated_at` timestamp

### Requirement: Rotation timeout
The system SHALL enforce a global rotation timeout of 5 minutes per rotation attempt.

#### Scenario: Rotation exceeds timeout
- **WHEN** a rotation operation exceeds 5 minutes
- **THEN** the system cancels the operation, transitions to `FAIL`, and triggers notification
