## Audit Event Types

### Event Types

| Event Type | Description | Severity | Status |
|---|---|---|---|
| `WEBHOOK_RECEIVED` | Webhook alert received and processed | INFO | `SUCCESS` |
| `VERIFICATION_STARTED` | Credential verification initiated | INFO | (none) |
| `VERIFICATION_COMPLETED` | Verification finished | INFO | `SUCCESS` or `FAILURE` |
| `RULE_EVALUATED` | Decision rule evaluated | AUDIT | (none) |
| `ACTION_EXECUTED` | Rotation action executed | AUDIT | `SUCCESS` or `FAILURE` |
| `DLQ_ENQUEUED` | Alert moved to dead letter queue | ERROR | `FAILURE` |
| `DEDUP_HIT` | Duplicate alert detected | WARN | (none) |

### Event Data Structure

Each event type carries structured JSON data in the `event_data` column:

#### WEBHOOK_RECEIVED
```json
{
  "provider": "gitguardian",
  "url": "https://api.example.com/alerts",
  "rawPayloadSize": 1024
}
```

#### VERIFICATION_STARTED
```json
{
  "alertId": "alert-123",
  "credentialType": "aws_access_key",
  "provider": "aws"
}
```

#### VERIFICATION_COMPLETED
```json
{
  "alertId": "alert-123",
  "result": "EXPIRED",
  "success": true,
  "durationMs": 250
}
```

#### RULE_EVALUATED
```json
{
  "ruleName": "check-expired-credentials",
  "matched": true,
  "reasoning": "Credential expired 30 days ago",
  "playbook": "immediate-rotation"
}
```

#### ACTION_EXECUTED
```json
{
  "actionType": "rotate-password",
  "provider": "aws",
  "success": true,
  "durationMs": 1500
}
```

#### DLQ_ENQUEUED
```json
{
  "error": "Invalid webhook signature",
  "rawPayload": "{\"malformed\": true}",
  "attempts": 3
}
```

#### DEDUP_HIT
```json
{
  "dedupLevel": "event",
  "cooldownState": "active",
  "originalAlertId": "alert-123"
}
```

### Database Schema

```sql
CREATE TABLE audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    client_id VARCHAR(100),
    alert_id VARCHAR(100),
    phase VARCHAR(50),
    trace_id VARCHAR(100),
    step VARCHAR(100),
    event_data JSONB NOT NULL,
    duration_ms INTEGER,
    status VARCHAR(20),
    created_at TIMESTAMP DEFAULT NOW()
);
```
