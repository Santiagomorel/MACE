# API Documentation

This document describes all API endpoints across the Credential Rotation System, including request/response examples, error codes, and authentication requirements.

---

## Base URLs

| Environment | Base URL |
|-------------|----------|
| Local (dev) | `http://localhost:8082` (alert-integrator) |
| Staging | `https://staging.company.com` |
| Production | `https://api.company.com` |

## Authentication

| Endpoint | Auth Method | Header |
|----------|-------------|--------|
| `POST /api/v1/alerts` | HMAC-SHA256 signature + IP whitelist | `X-GitGuardian-Signature` |
| `POST/GET /api/*` (admin) | API key | `X-API-Key` |
| All others | No auth (public) | — |

## Error Response Format

All error responses follow the `ErrorResponse` record format:

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "path": "/api/v1/alerts",
  "message": "Request validation failed",
  "details": ["providerName: must not be blank", "credentialType: must be one of [AWS_ACCESS_KEY, IAM_USER, RDS_CREDENTIAL, GENERIC]"]
}
```

---

## Alert Ingestion (alert-integrator)

### POST `/api/v1/alerts`

Receives webhook alerts from security scanners (GitGuardian, etc.).

**Authentication:** HMAC-SHA256 signature validation + IP whitelist

**Headers:**
| Header | Required | Description |
|--------|----------|-------------|
| `Content-Type` | Yes | `application/json` |
| `X-GitGuardian-Signature` | Yes (prod) | HMAC-SHA256 signature of request body |

**Request Body:**

```json
{
  "source": "gitguardian",
  "providerName": "gitguardian",
  "credentialType": "AWS_ACCESS_KEY",
  "tenantId": "tenant-123",
  "incident": {
    "id": "evt_abc123",
    "secret_type": "AWS Access Key",
    "value_hash": "sha256_hash_here",
    "detector": "aws-access-key",
    "repository": "github.com/company/myapp",
    "file": "src/config/aws.py",
    "commit": "abc123def456",
    "line": 42,
    "visibility": "public",
    "created_at": "2024-01-15T10:30:00Z",
    "severity": "high",
    "is_new": true,
    "previously_flagged": false,
    "flag_count": 0
  }
}
```

**Responses:**

| Status | Body | Description |
|--------|------|-------------|
| `201 Created` | `{"status": "accepted", "alertId": "550e8400-e29b-41d4-a716-446655440000"}` | Alert queued for processing |
| `200 OK` | `{"status": "duplicate_skipped"}` | Event-level dedup hit (same `sourceEventId` within 5 min) |
| `200 OK` | `{"status": "secret_dedup_cooldown", "alertId": "...", "cooldownExpiry": "..."}` | Secret-level dedup — within cooldown period |
| `200 OK` | `{"status": "secret_in_progress", "alertId": "...", "linkedAlert": "..."}` | Secret already being processed |
| `401 Unauthorized` | `{"error": "INVALID_SIGNATURE"}` | HMAC signature mismatch |
| `401 Unauthorized` | `{"error": "MISSING_SIGNATURE"}` | No signature header provided |
| `403 Forbidden` | `{"error": "IP_FORBIDDEN"}` | Source IP not in whitelist |
| `400 Bad Request` | `ErrorResponse` | Invalid payload or unknown provider |
| `429 Too Many Requests` | — | Worker pool queue full (backpressure) |
| `500 Internal Server Error` | `{"status": "error", "message": "Internal processing error", "request_id": "..."}` | Unexpected pipeline error |

**Example Request:**

```bash
SECRET="my-shared-secret"
PAYLOAD='{"source":"gitguardian","providerName":"gitguardian","credentialType":"AWS_ACCESS_KEY","tenantId":"tenant-123","incident":{"id":"evt_abc123"}}'
SIG=$(echo -n "$PAYLOAD" | openssl dgst -sha256 -hmac "$SECRET" | awk '{print $2}')

curl -X POST http://localhost:8082/api/v1/alerts \
  -H "Content-Type: application/json" \
  -H "X-GitGuardian-Signature: $SIG" \
  -d "$PAYLOAD"
```

---

### Admin Endpoints (alert-integrator)

All `/api/v1/admin/**` endpoints require `X-API-Key` header authentication.

#### GET `/api/v1/admin/health`

Returns the service health status.

**Headers:**
| Header | Required | Description |
|--------|----------|-------------|
| `X-API-Key` | Yes | Admin API key |

**Response (200 OK):**

```json
{
  "status": "healthy",
  "components": {
    "database": "up",
    "cache": "up",
    "workerPool": "running",
    "workers": 5,
    "queueSize": 12
  },
  "uptime": "2h 15m 30s"
}
```

#### GET `/api/v1/admin/dlq`

Lists Dead Letter Queue entries.

**Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| `status` | string | Filter by status (`pending`, `retrying`, `archived`) |
| `limit` | int | Max results (default: 50, max: 100) |
| `offset` | int | Pagination offset (default: 0) |

**Response (200 OK):**

```json
{
  "total": 12,
  "items": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "source": "gitguardian",
      "payload": {"incident": {"id": "evt_xxx"}},
      "errorMessage": "Verification provider unreachable",
      "retryCount": 2,
      "maxRetries": 3,
      "status": "retrying",
      "createdAt": "2024-01-15T08:00:00Z",
      "lastAttemptAt": "2024-01-15T09:30:00Z"
    }
  ]
}
```

#### POST `/api/v1/admin/dlq/{id}/retry`

Retries a single DLQ entry.

**Response (200 OK):**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "retry_queued"
}
```

---

## Decision Engine (decision-engine)

### GET `/api/{tenantId}/rules`

Returns Drools rules for a tenant.

**Headers:**
| Header | Required | Description |
|--------|----------|-------------|
| `X-API-Key` | Yes | Admin API key |

**Response (200 OK):**

```json
{
  "tenantId": "tenant-123",
  "version": "1.2.0",
  "drlContent": "package com.company.rotations.rules.tenant_123\n\nrule \"rotate_verified_high_severity\"\n  when\n    $result : VerificationResult(verified == true, severityScope == \"HIGH\")\n  then\n    $result.setDecision(Decision.ROTATE);\nend",
  "drlSizeBytes": 2048,
  "active": true,
  "manualOverride": false,
  "createdAt": "2024-01-10T08:00:00Z"
}
```

### POST `/api/{tenantId}/rules`

Updates severity override for a tenant.

**Headers:**
| Header | Required | Description |
|--------|----------|-------------|
| `X-API-Key` | Yes | Admin API key |

**Request Body:**

```json
{
  "severity": "ALTO",
  "manualOverride": true,
  "user": "admin@example.com"
}
```

**Valid severity values:** `BAJO`, `MEDIA`, `ALTO`, `CRITICO`

**Response (200 OK):**

```json
{
  "applied": true,
  "severity": "ALTO",
  "manualOverride": true,
  "user": "admin@example.com"
}
```

**Response (400 Bad Request) — Below playbook floor:**

```json
{
  "error": "Cannot lower below playbook floor: ALTO",
  "playbookFloor": "ALTO"
}
```

### POST `/api/{tenantId}/rules/discover`

Triggers AWS metadata discovery and auto-regenerates rules.

**Headers:**
| Header | Required | Description |
|--------|----------|-------------|
| `X-API-Key` | Yes | Admin API key |

**Request Body:**

```json
{
  "awsAccessKeyId": "AKIAIOSFODNN7EXAMPLE",
  "awsSecretAccessKey": "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
}
```

**Responses:**

| Status | Body | Description |
|--------|------|-------------|
| `200 OK` | `{"status": "success", "tenantId": "..."}` | Discovery completed successfully |
| `200 OK` | `{"status": "updated", "newVersion": "1.3.0", "drlHash": "abc123..."}` | Rules were updated |
| `200 OK` | `{"status": "no_changes"}` | No permission changes detected |
| `401 Unauthorized` | `{"status": "credential_expired", "state": "expired"}` | AWS credentials expired |
| `422 Unprocessable Entity` | `{"status": "validation_failed"}` | Generated DRL failed validation |
| `500 Internal Server Error` | `{"status": "error", "message": "..."}` | Discovery failed |

### GET `/api/playbooks`

Returns all available playbooks.

**Response (200 OK):**

```json
[
  {
    "playbookId": "aws-access-key-exposed",
    "version": "1.0.0",
    "credentialTypes": ["AKIA"],
    "severityFloor": {
      "s3_full_access": "CRITICO",
      "s3_read_only": "ALTO",
      "iam_modify": "CRITICO",
      "ec2_instance_control": "CRITICO",
      "cloudwatch_read": "MEDIA",
      "nothing_active": "BAJO"
    },
    "autoRotate": {"enabled": true, "maxWindowMins": 15},
    "canLowerFloor": false
  }
]
```

### POST `/api/validate-drl`

Validates Drools rule syntax before hot-reloading.

**Request Body:**

```json
{
  "drl": "package com.company.rotations.rules\n\nrule \"test_rule\"\n  when\n    $alert : Alert(severity == \"HIGH\")\n  then\n    System.out.println(\"High severity alert\");\nend"
}
```

**Response (200 OK):**

```json
{
  "valid": true,
  "errors": 0
}
```

**Response (200 OK) — Invalid rule:**

```json
{
  "valid": false,
  "errors": 3
}
```

---

## Health Check Endpoints (all modules)

### GET `/actuator/health`

Spring Boot health check.

**Response (200 OK) — Healthy:**

```json
{
  "status": "UP",
  "components": {
    "diskSpace": {
      "status": "UP",
      "total": 10737418240,
      "free": 5368709120,
      "threshold": 1048576
    },
    "db": {
      "status": "UP",
      "database": "PostgreSQL",
      "validationQuery": "SELECT 1"
    },
    "livenessState": {
      "status": "Alive"
    },
    "readinessState": {
      "status": "Ready"
    }
  }
}
```

**Response (200 OK) — Unhealthy:**

```json
{
  "status": "DOWN",
  "components": {
    "db": {
      "status": "DOWN",
      "error": "Cannot connect to database"
    }
  }
}
```

### GET `/actuator/health/readiness`

Readiness probe for load balancer.

**Response (200 OK) — Ready:** `{"status": "UP"}`
**Response (503 Service Unavailable):** `{"status": "DOWN"}`

### GET `/actuator/health/liveness`

Liveness probe for container orchestration.

**Response (200 OK) — Alive:** `{"status": "UP"}`
**Response (503 Service Unavailable):** `{"status": "DOWN"}`

### GET `/actuator/prometheus`

Prometheus metrics scrape endpoint.

**Response (200 OK) — Text format:**

```
# HELP http_server_requests_seconds
# TYPE http_server_requests_seconds summary
http_server_requests_seconds_count{method="POST",uri="/api/v1/alerts",status="201",exception="none"} 150.0
http_server_requests_seconds_sum{method="POST",uri="/api/v1/alerts",status="201",exception="none"} 2.345
# HELP alerts_ingested_total
# TYPE alerts_ingested_total counter
alerts_ingested_total 150
# HELP alerts_deduplicated_total
# TYPE alerts_deduplicated_total counter
alerts_deduplicated_total 23
```

---

## Decision Engine Webhooks

### POST `/api/webhooks/credential-exposure`

Receives credential exposure events directly from internal services.

**Headers:**
| Header | Required | Description |
|--------|----------|-------------|
| `Content-Type` | Yes | `application/json` |
| `X-Webhook-Source` | No | Source identifier (default: `"unknown"`) |
| `X-Webhook-Signature` | No | Optional signature |

**Request Body:**

```json
{
  "tenantId": "tenant-123",
  "resource": "AKIAIOSFODNN7EXAMPLE",
  "awsCredentials": {
    "accessKeyId": "AKIAIOSFODNN7EXAMPLE",
    "secretAccessKey": "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
  }
}
```

**Responses:**

| Status | Body | Description |
|--------|------|-------------|
| `200 OK` | `{"status": "updated", "tenantId": "...", "newVersion": "1.3.0"}` | Rules updated |
| `200 OK` | `{"status": "no_changes", "tenantId": "..."}` | No changes detected |
| `202 Accepted` | `{"status": "queued", "tenantId": "...", "message": "Webhook received. Discovery queued but credentials not provided..."}` | Queued for processing |
| `400 Bad Request` | `{"status": "error", "message": "tenantId is required in webhook payload"}` | Missing tenantId |
| `401 Unauthorized` | `{"status": "credential_expired", "state": "expired"}` | AWS credentials expired |
| `422 Unprocessable Entity` | `{"status": "validation_failed", "message": "..."}` | DRL validation failed |
| `500 Internal Server Error` | `{"status": "error", "message": "..."}` | Discovery failed |

### POST `/api/webhooks/discovery/pull`

Triggers pull discovery for a single tenant.

**Request Body:**

```json
{
  "tenantId": "tenant-123",
  "awsCredentials": {
    "accessKeyId": "AKIAIOSFODNN7EXAMPLE",
    "secretAccessKey": "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
  }
}
```

**Response (200 OK):**

```json
{
  "status": "updated",
  "tenantId": "tenant-123",
  "source": "pull:internal",
  "newVersion": "1.3.0"
}
```

### POST `/api/webhooks/discovery/batch`

Triggers batch discovery for multiple tenants.

**Request Body:**

```json
{
  "tenantIds": ["tenant-123", "tenant-456", "tenant-789"],
  "awsCredentials": {
    "accessKeyId": "AKIAIOSFODNN7EXAMPLE",
    "secretAccessKey": "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
  }
}
```

**Response (200 OK):**

```json
{
  "status": "completed",
  "source": "batch:internal",
  "processed": 3,
  "updated": 1,
  "skipped": 1,
  "failed": 1,
  "results": [
    {"tenantId": "tenant-123", "status": "updated", "newVersion": "1.3.0"},
    {"tenantId": "tenant-456", "status": "no_changes"},
    {"tenantId": "tenant-789", "status": "error", "message": "Credentials expired"}
  ]
}
```

---

## Error Codes Reference

| Error Code | HTTP Status | Description |
|------------|-------------|-------------|
| `INVALID_SIGNATURE` | 401 | HMAC signature verification failed |
| `MISSING_SIGNATURE` | 401 | No signature header provided |
| `IP_FORBIDDEN` | 403 | Source IP not in whitelist |
| `VALIDATION_ERROR` | 400 | Request body validation failed |
| `TECHNICAL_ERROR` | 500 | Unexpected system error |
| `INTERNAL_ERROR` | 500 | Unhandled exception |
| `NOT_FOUND` | 404 | Resource not found |
| `RATE_LIMITED` | 429 | Too many requests (backpressure) |
