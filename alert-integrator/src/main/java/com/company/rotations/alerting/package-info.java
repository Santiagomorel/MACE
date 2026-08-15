/**
 * <h2>Alert Integrator Module</h2>
 *
 * <p>The alert integrator is the credential exposure ingestion layer of the
 * Credential Rotation System. It receives real-time alerts from security
 * scanners via webhooks, normalizes them into a generic model, deduplicates
 * at event and secret levels, and dispatches them to a worker pool for
 * downstream processing.</p>
 *
 * <h3>Architecture Overview</h3>
 *
 * <pre>
 * Webhook (HTTP) ──▶ Signature Validation ──▶ IP Whitelist
 *                                    │
 *                                    ▼
 *                          Source Detection ──▶ Adapter Lookup
 *                                    │
 *                                    ▼
 *                          Event Dedup (TTL 5min)
 *                                    │
 *                                    ▼
 *                          Secret Dedup (state-based cooldown)
 *                                    │
 *                                    ▼
 *                          WebhookController → DeadLetterQueueService
 *                                    │
 *                                    ▼
 *                          WorkerPool (configurable size)
 *                                    │
 *                                    ▼
 *                          Credential Verifier → Decision Engine
 * </pre>
 *
 * <h3>Package Organization</h3>
 *
 * <dl>
 *   <dt>adapter</dt>
 *   <dd>Provider-specific adapters that map raw webhook payloads to
 *       {@link com.company.rotations.models.GenericAlertModel}.
 *       Includes {@link com.company.rotations.alerting.adapter.GitGuardianAdapter},
 *       {@link com.company.rotations.alerting.adapter.DefaultAdapter}, and
 *       {@link com.company.rotations.alerting.adapter.AdapterRegistry}.</dd>
 *
 *   <dt>config</dt>
 *   <dd>Spring configuration classes: exception handlers, CORS, web
 *       configuration, worker pool configuration, and custom exception types.</dd>
 *
 *   <dt>controller</dt>
 *   <dd>The webhook REST endpoint and its validation helpers.
 *       {@link com.company.rotations.alerting.controller.WebhookController}
 *       orchestrates the ingestion pipeline.
 *       {@link com.company.rotations.alerting.controller.SignatureValidator}
 *       and {@link com.company.rotations.alerting.controller.IpWhitelistValidator}
 *       provide authentication layers.</dd>
 *
 *   <dt>dedup</dt>
 *   <dd>Two-level deduplication:
 *       {@link com.company.rotations.alerting.dedup.EventDedupService}
 *       (event-level, TTL-based) and
 *       {@link com.company.rotations.alerting.dedup.SecretDedupService}
 *       (secret-level, state-based cooldowns).</dd>
 *
 *   <dt>dlq</dt>
 *   <dd>Dead Letter Queue for failed alerts.
 *       {@link com.company.rotations.alerting.dlq.DeadLetterQueueService}
 *       handles insertion, retrieval, retry, and cleanup.
 *       {@link com.company.rotations.alerting.dlq.AlertDLQEntry} is the
 *       persistence model.</dd>
 *
 *   <dt>dto</dt>
 *   <dd>Data transfer objects used in the REST API.</dd>
 *
 *   <dt>interceptor</dt>
 *   <dd>API key authentication interceptor for protected endpoints.</dd>
 *
 *   <dt>model</dt>
 *   <dd>Internal domain models such as {@link com.company.rotations.alerting.model.WebhookPayload}.</dd>
 *
 *   <dt>validation</dt>
 *   <dd>Custom JSR-303/380 constraint annotations and validators.</dd>
 *
 *   <dt>worker</dt>
 *   <dd>{@link com.company.rotations.alerting.worker.WorkerPool} — fixed-size
 *       thread pool with backpressure and DLQ integration.</dd>
 * </dl>
 *
 * <h3>Deduplication Strategy</h3>
 *
 * <p><b>Level 1 — Event Dedup:</b> Uses the provider's event ID as key with a
 * configurable TTL (default 5 minutes). Prevents re-processing of the same
 * event during provider retries.</p>
 *
 * <p><b>Level 2 — Secret Dedup:</b> Uses {@code valueHash + repository} as key.
 * State is driven by the credential verifier's output:</p>
 *
 * <ul>
 *   <li>{@code false_positive} → 24-hour cooldown (FP secrets should not re-alert frequently)</li>
 *   <li>{@code true_positive} + action completed → 1-hour cooldown</li>
 *   <li>{@code in_progress} → immediate skip</li>
 * </ul>
 *
 * <p>Cooldowns are configurable via application properties. Both caches use
 * Caffeine (in-memory, local). If multi-instance deployment is needed in the
 * future, migrate to Redis.</p>
 *
 * <h3>Adding a New Provider Adapter</h3>
 *
 * <ol>
 *   <li>Create a class implementing
 *       {@link com.company.rotations.spi.AlertAdapter}</li>
 *   <li>Annotate with {@code @Component} so Spring discovers it automatically</li>
 *   <li>Implement {@code getProviderName()} — must match the {@code source}
 *       field in incoming webhooks</li>
 *   <li>Implement {@code toGenericAlert(Map&lt;String, Object&gt;)} to map the
 *       provider's raw payload to {@link com.company.rotations.models.GenericAlertModel}</li>
 *   <li>The {@link com.company.rotations.alerting.adapter.AdapterRegistry} will
 *       register it automatically</li>
 * </ol>
 *
 * <h3>Configuration</h3>
 *
 * <table>
 *   <tr><th>Property</th><th>Default</th><th>Description</th></tr>
 *   <tr><td>{@code app.alerting.webhook.path}</td><td>{@code /api/v1/alerts}</td><td>Webhook endpoint path</td></tr>
 *   <tr><td>{@code app.alerting.worker-pool-size}</td><td>{@code 5}</td><td>Number of worker threads</td></tr>
 *   <tr><td>{@code app.alerting.queue-max-size}</td><td>{@code 1000}</td><td>Max queue capacity</td></tr>
 *   <tr><td>{@code app.alerting.event-dedup-ttl-minutes}</td><td>{@code 5}</td><td>Event dedup TTL</td></tr>
 *   <tr><td>{@code app.alerting.secret-dedup.false-positive-cooldown-hours}</td><td>{@code 24}</td><td>FP cooldown duration</td></tr>
 *   <tr><td>{@code app.alerting.secret-dedup.true-positive-cooldown-hours}</td><td>{@code 1}</td><td>TP cooldown duration</td></tr>
 *   <tr><td>{@code app.providers.gitguardian.shared-secret}</td><td>—</td><td>HMAC secret for signature validation</td></tr>
 *   <tr><td>{@code app.providers.gitguardian.signature-header}</td><td>{@code X-GitGuardian-Signature}</td><td>Signature header name</td></tr>
 *   <tr><td>{@code app.providers.gitguardian.allowed-ips}</td><td>—</td><td>IP whitelist (comma-separated)</td></tr>
 * </table>
 *
 * @see com.company.rotations.spi.AlertAdapter
 * @see com.company.rotations.models.GenericAlertModel
 */
package com.company.rotations.alerting;
