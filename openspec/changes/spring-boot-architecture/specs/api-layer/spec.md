# API Layer

## ADDED Requirements

### Requirement: REST API v1 with URL versioning
The system SHALL expose all public API endpoints under the `/api/v1/` URL prefix.

#### Scenario: Alert ingestion endpoint
- **WHEN** a client sends `POST /api/v1/alerts`
- **THEN** the system returns HTTP 201 Created with the alert ID

#### Scenario: Verification status endpoint
- **WHEN** a client sends `GET /api/v1/verification/{alertId}`
- **THEN** the system returns HTTP 200 with the verification result or 404 if not found

#### Scenario: Admin rules endpoint
- **WHEN** a client sends `GET /api/v1/admin/rules` with a valid API key
- **THEN** the system returns HTTP 200 with the list of Drools rules

### Requirement: Health check endpoints
The system SHALL expose health check endpoints under Spring Actuator at `/actuator/health` without URL versioning.

#### Scenario: Readiness probe
- **WHEN** a load balancer sends a request to `/actuator/health/readiness`
- **THEN** the system returns HTTP 200 when the application is ready to receive traffic

#### Scenario: Liveness probe
- **WHEN** a load balancer sends a request to `/actuator/health/liveness`
- **THEN** the system returns HTTP 200 when the application is alive

### Requirement: HMAC-SHA256 webhook signature validation
The system SHALL validate the `X-Signature` header on all webhook POST requests using HMAC-SHA256 with a shared secret.

#### Scenario: Valid webhook signature
- **WHEN** a POST request to `/api/v1/alerts` includes a valid `X-Signature` header (HMAC-SHA256 of the request body with the configured webhook secret)
- **THEN** the system processes the alert and returns HTTP 201

#### Scenario: Invalid webhook signature
- **WHEN** a POST request to `/api/v1/alerts` includes an invalid `X-Signature` header
- **THEN** the system returns HTTP 401 Unauthorized with an error message

#### Scenario: Missing webhook signature
- **WHEN** a POST request to `/api/v1/alerts` omits the `X-Signature` header
- **THEN** the system returns HTTP 401 Unauthorized with an error message

### Requirement: Admin API key authentication
The system SHALL require an `X-API-Key` header for all `/api/v1/admin/` endpoints.

#### Scenario: Admin endpoint with valid API key
- **WHEN** a request to `/api/v1/admin/rules` includes a valid `X-API-Key` header
- **THEN** the system processes the request

#### Scenario: Admin endpoint with missing API key
- **WHEN** a request to `/api/v1/admin/rules` omits the `X-API-Key` header
- **THEN** the system returns HTTP 401 Unauthorized

### Requirement: CORS configuration
The system SHALL enforce strict CORS configuration that only allows explicitly configured origins.

#### Scenario: Allowed origin in dev profile
- **WHEN** the application runs in `dev` profile and a request comes from any origin
- **THEN** CORS headers are set to allow `*`

#### Scenario: Allowed origin in prod profile
- **WHEN** the application runs in `prod` profile and a request comes from an origin NOT in `ALLOWED_ORIGINS`
- **THEN** the browser rejects the response due to CORS policy

#### Scenario: Allowed origin in prod profile (allowed)
- **WHEN** the application runs in `prod` profile and a request comes from an origin listed in `ALLOWED_ORIGINS`
- **THEN** CORS headers are set to allow the request

### Requirement: Bean Validation on request bodies
The system SHALL validate all incoming request bodies using Bean Validation (JSR-380) annotations.

#### Scenario: Missing required field
- **WHEN** a POST request to `/api/v1/alerts` omits the `providerName` field
- **THEN** the system returns HTTP 400 Bad Request with a validation error detail

#### Scenario: Invalid enum value
- **WHEN** a POST request to `/api/v1/alerts` includes an invalid `credentialType` value
- **THEN** the system returns HTTP 400 Bad Request with a validation error indicating valid values

#### Scenario: Valid request body
- **WHEN** a POST request to `/api/v1/alerts` includes all required fields with valid values
- **THEN** the system proceeds to process the alert

### Requirement: Global error handling
The system SHALL use `@ControllerAdvice` with `@ExceptionHandler` to provide consistent error responses across all endpoints.

#### Scenario: Business error response format
- **WHEN** a `BadRequestException` is thrown
- **THEN** the system returns HTTP 400 with a JSON body containing `timestamp`, `status`, `error`, `path`, `message`, and optional `details`

#### Scenario: Technical error response format
- **WHEN** a `TechnicalException` is thrown
- **THEN** the system returns HTTP 500 with a generic error body that does NOT expose stack traces or internal details

#### Scenario: Unexpected error logging
- **WHEN** an `Exception` is thrown that is not handled by a more specific handler
- **THEN** the system logs the error with full context (MDC fields: alertId, tenantId, sessionId) and returns a generic HTTP 500 response
