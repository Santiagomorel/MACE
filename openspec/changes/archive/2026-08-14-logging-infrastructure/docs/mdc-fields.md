## MDC Fields Reference

### Fields

| Field | Source | Example | Description |
|---|---|---|---|
| `trace_id` | `X-Trace-Id` header or generated UUID | `550e8400-e29b-41d4-a716-446655440000` | End-to-end request correlation ID. Generated if not provided. |
| `alert_id` | `sourceEventId` param, `X-Source-Event-Id` header, or generated UUID | `alert-xyz-789` | Identifies the specific alert being processed. |
| `client_id` | `X-Client-Id` or `X-Tenant-Id` header | `acme-corp` | Tenant/client identifier for multi-tenant isolation. |
| `phase` | Set by consuming modules via `AuditService.log*()` methods | `alert-ingestion` | Current pipeline phase: `alert-ingestion`, `verification`, `decision`, `action-execution`. |
| `step` | Set by consuming modules for sub-operations | `webhook-validated` | Specific step within the phase. |

### How Fields Are Populated

1. **`MdcLoggingFilter`** runs on every request and sets `trace_id`, `alert_id`, and `client_id`.
2. **`AuditService`** reads phase/trace/alert from MDC when creating audit events.
3. **Consuming modules** set `phase` and `step` before calling `AuditService` methods.

### MDC Cleanup

The `MdcLoggingFilter` clears all MDC fields in a `finally` block to prevent MDC pollution across requests. This is critical in async/thread-pooled environments.
