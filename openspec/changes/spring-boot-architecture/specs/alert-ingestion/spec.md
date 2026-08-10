# Alert Ingestion

## ADDED Requirements

### Requirement: Webhook alert ingestion endpoint
The system SHALL expose a `POST /api/v1/alerts` endpoint that accepts webhook payloads from external providers.

#### Scenario: Valid webhook payload
- **WHEN** a POST request to `/api/v1/alerts` contains a valid JSON payload with `providerName`, `credentialType`, and `tenantId`
- **THEN** the system processes the alert and returns HTTP 201 with the alert ID

#### Scenario: Unknown provider
- **WHEN** a POST request to `/api/v1/alerts` contains an unknown `providerName`
- **THEN** the system returns HTTP 400 with an error listing registered providers

### Requirement: Provider adapter pattern
The system SHALL use an adapter pattern where each alert provider (e.g., GitGuardian) has a concrete adapter implementing the `AlertAdapter` SPI interface.

#### Scenario: GitGuardian adapter registration
- **WHEN** the application starts
- **THEN** the `GitGuardianAdapter` is registered in the `AdapterRegistry` with `providerName = "gitguardian"`

#### Scenario: Adapter selection by provider name
- **WHEN** a webhook arrives with `providerName = "gitguardian"`
- **THEN** the `AdapterRegistry` returns the `GitGuardianAdapter` instance

#### Scenario: Unknown provider adapter
- **WHEN** a webhook arrives with an unregistered `providerName`
- **THEN** the `AdapterRegistry` throws a `NotFoundException`

### Requirement: Event deduplication (5-minute TTL)
The system SHALL deduplicate alerts based on event signature within a 5-minute window using an in-memory Caffeine cache.

#### Scenario: Duplicate alert within window
- **WHEN** the same event (same alert type, credential type, tenant, and severity hash) arrives twice within 5 minutes
- **THEN** the second alert is marked as a duplicate and skipped (no processing)

#### Scenario: Alert outside dedup window
- **WHEN** the same event arrives more than 5 minutes after the previous occurrence
- **THEN** the system processes it as a new alert

### Requirement: Secret deduplication (configurable cooldown)
The system SHALL deduplicate alerts based on secret exposure with a configurable cooldown period per secret type.

#### Scenario: Same secret exposed within cooldown
- **WHEN** the same AWS access key ID is reported as exposed within its configured cooldown period
- **THEN** the alert is deduplicated and linked to the original alert

#### Scenario: Different secret exposed
- **WHEN** a different secret from the same tenant is reported as exposed
- **THEN** the system processes it as a new alert

### Requirement: Worker pool for alert processing
The system SHALL process alerts asynchronously using a configurable worker pool (default: 5 concurrent workers).

#### Scenario: Alert queued for processing
- **WHEN** a validated alert arrives at the ingestion endpoint
- **THEN** the alert is submitted to the worker pool for async processing and the endpoint returns immediately

#### Scenario: Worker pool at capacity
- **WHEN** all 5 workers are busy and a new alert arrives
- **THEN** the alert is queued (bounded queue, default: 1000) and processed when a worker becomes available

### Requirement: Dead Letter Queue (DLQ)
The system SHALL persist alerts that fail processing to a PostgreSQL `alert_dlq` table.

#### Scenario: Processing failure
- **WHEN** an alert fails to process (e.g., verification provider unreachable)
- **THEN** the alert is persisted to the `alert_dlq` table with error details and retry count

#### Scenario: DLQ alert inspection
- **WHEN** an operator queries the `alert_dlq` table
- **THEN** the system returns alerts with their original payload, error message, retry count, and failure timestamp
