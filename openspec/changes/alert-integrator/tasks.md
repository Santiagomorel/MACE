## 1. Module Setup and Configuration

- [ ] 1.1 Create package organization for alerting module (controller, adapter, dedup, worker, dlq)
- [ ] 1.2 Add Caffeine dependency to build configuration for local in-memory cache
- [ ] 1.3 Configure webhook endpoint path `/api/alerts` in application properties
- [ ] 1.4 Configure worker pool size (default 5), event dedup TTL (default 5 min), secret dedup cooldowns in application properties
- [ ] 1.5 Define `GenericAlertModel` data model with all fields as specified in design
- [ ] 1.6 Define `ProviderAdapter` interface with `toGenericAlert(rawPayload)` method contract
- [ ] 1.7 Create `AdapterRegistry` to manage provider-to-adapter mapping

## 2. Webhook Endpoint and Authentication

- [ ] 2.1 Create REST controller for `POST /api/alerts` endpoint
- [ ] 2.2 Implement signature validation using HMAC-SHA256 (configurable per provider)
- [ ] 2.3 Implement IP whitelist validation (configurable per provider)
- [ ] 2.4 Add structured logging for all webhook arrivals (valid and invalid)
- [ ] 2.5 Return appropriate HTTP status codes (200 for success/dedup skip, 401 for signature failure, 403 for IP blocked)
- [ ] 2.6 Write unit tests for signature validation (valid/invalid signatures, missing headers)
- [ ] 2.7 Write unit tests for IP whitelist (allowed IP, blocked IP, missing IP header)

## 3. Adapter Pattern Implementation

- [ ] 3.1 Implement `GitGuardianAdapter` that maps GitGuardian API v2 payload to `GenericAlertModel`
  - Map `incident` fields to `context` (repository, file, commit, line, visibility)
  - Map `incident.secret_type` to `detectedSecret.type`
  - Map `incident.id` to `sourceEventId`
  - Copy `providerSeverity` if available
  - Set `detectorState` with `isNew` and `previouslyFlagged` based on GitGuardian metadata
- [ ] 3.2 Implement `DefaultAdapter` as fallback for unknown sources (basic parse, type = "generic")
- [ ] 3.3 Register `GitGuardianAdapter` in `AdapterRegistry` on startup
- [ ] 3.4 Write unit tests for GitGuardianAdapter (mapping all fields, handling missing optional fields)
- [ ] 3.5 Write unit tests for AdapterRegistry (found adapter, missing adapter → default)

## 4. Event-Level Deduplication

- [ ] 4.1 Create `EventDedupService` using Caffeine cache
  - Key: `sourceEventId` (hashed)
  - Value: arrival timestamp
  - TTL: configurable (default 5 minutes)
- [ ] 4.2 Integrate event dedup into the webhook processing pipeline (check before adapter)
- [ ] 4.3 Handle cache eviction and TTL expiration correctly
- [ ] 4.4 Write unit tests for event dedup (hit, miss, TTL expiration, concurrent access)

## 5. Secret-Level Deduplication with State

- [ ] 5.1 Create `SecretDedupService` using Caffeine cache with composite key
  - Key: `valueHash + repository`
  - Value: `SecretDedupEntry { status, timestamp, cooldownMillis }`
- [ ] 5.2 Implement cooldown logic based on verifier result state:
  - `false_positive` → cooldown 24 hours
  - `true_positive` + action completed → cooldown 1 hour
  - `in_progress` → immediate skip
- [ ] 5.3 Implement state update mechanism: when verifier completes, update secret dedup entry with result
- [ ] 5.4 Write unit tests for secret dedup (all state transitions, cooldown periods, concurrent access)

## 6. Webhook Processing Pipeline

- [ ] 6.1 Create `WebhookPipeline` that orchestrates: parse → validate auth → event dedup → secret dedup → normalize → queue
- [ ] 6.2 Integrate adapter selection into the pipeline
- [ ] 6.3 Handle pipeline failures gracefully (send to DLQ, log error, return HTTP 200)
- [ ] 6.4 Add metrics collection: pipeline duration, dedup hit rate, adapter routing stats
- [ ] 6.5 Write integration test for complete pipeline with mocked adapter and cache

## 7. Worker Pool and Async Processing

- [ ] 7.1 Create `WorkerPool` with configurable size (default 5 workers)
- [ ] 7.2 Implement queue-based task submission (alerts enter queue, workers pull from queue)
- [ ] 7.3 Each worker executes: process alert → call verifier → update secret dedup state → send to decision engine (if applicable)
- [ ] 7.4 Implement backpressure: queue has max size, reject or DLQ when full
- [ ] 7.5 Write unit tests for worker pool (concurrent processing, backpressure, worker failure)

## 8. Dead Letter Queue

- [ ] 8.1 Create database table `alert_dlq` with columns: id, raw_payload, error_message, source, source_event_id, retry_count, created_at, status
- [ ] 8.2 Implement `DeadLetterQueueService` for inserting failed alerts
- [ ] 8.3 Integrate DLQ into the pipeline (parsing failures, adapter failures, processing failures after max retries)
- [ ] 8.4 Implement DLQ cleanup task (delete entries older than 7 days)
- [ ] 8.5 Write unit tests for DLQ (insert, retrieve, cleanup, max retries)

## 9. Credential Verifier Integration (Modified)

- [ ] 9.1 Update verifier input to accept `GenericAlertModel` instead of GitGuardian-specific format
- [ ] 9.2 Verify that provider detection works with explicit `detectedSecret.type` from model
- [ ] 9.3 Verify that heuristic fallback still works when `detectedSecret.type` is "generic"
- [ ] 9.4 Remove dependency on GitGuardian-specific fields (e.g., `account_hint`) from verifier
- [ ] 9.5 Update verifier output to include `result_type` (`true_positive` / `false_positive`) for secret dedup state updates
- [ ] 9.6 Write integration tests for verifier with generic alert model input

## 10. Secrets Handling and Security

- [ ] 10.1 Ensure `detectedSecret.valueHash` uses SHA-256 hash (never store raw secret values in cache or logs)
- [ ] 10.2 Add redaction for secret values in structured logging
- [ ] 10.3 Store webhook shared secrets in application properties or vault (not in code or repo)
- [ ] 10.4 Write security review for secret handling in dedup cache and DLQ

## 11. Testing and Validation

- [ ] 11.1 Write unit tests for GenericAlertModel serialization/deserialization
- [ ] 11.2 Write unit tests for all dedup scenarios (event hit/miss/expiry, secret all states)
- [ ] 11.3 Write unit tests for webhook controller (valid/invalid payloads, auth failures, IP blocked)
- [ ] 11.4 Write integration test for end-to-end flow: webhook → adapter → dedup → worker → verifier
- [ ] 11.5 Write integration test for DLQ flow (invalid payload → DLQ entry)
- [ ] 11.6 Write load test: simulate 100 concurrent webhook arrivals and verify worker pool behavior
- [ ] 11.7 Write test for FP scenario: verifier returns false_positive → secret cooldown 24h → re-send same secret → dedup skip

## 12. Documentation and Deployment

- [ ] 12.1 Add module-level documentation in the alerting package
- [ ] 12.2 Document the GenericAlertModel schema and adapter contract for future providers
- [ ] 12.3 Document webhook signature verification process for GitGuardian
- [ ] 12.4 Document open questions section (exact GG signature format, cooldown tuning, DLQ notification)
- [ ] 12.5 Verify end-to-end flow with a real GitGuardian webhook (sandbox/test)
- [ ] 12.6 Create monitoring dashboard: webhook throughput, dedup rates, worker utilization, DLQ depth
