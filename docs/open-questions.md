# Open Questions

This document tracks open questions identified in `design.md` and their resolution status.

---

## Questions

### Q1: GitGuardian Webhook Signature Format Exact

**Status:** 🔵 OPEN  
**Priority:** High  
**Owner:** (unassigned)

**Question:** Confirm the exact header and algorithm that GitGuardian uses to sign webhooks (documentation from their API v2).

**Context:** The system assumes HMAC-SHA256 with the `X-GitGuardian-Signature` header, but GitGuardian may use a different header name or algorithm for different event types.

**Resolution Criteria:**
- [ ] Test with actual GitGuardian webhook payloads
- [ ] Document the exact header name used
- [ ] Confirm the signing algorithm (HMAC-SHA256 vs. other)
- [ ] Update `SignatureValidator` configuration if needed

---

### Q2: TTL of Cooldown for true_positive

**Status:** 🔵 OPEN  
**Priority:** Medium  
**Owner:** (unassigned)

**Question:** Is 1 hour sufficient for the cooldown period after a confirmed true positive? Could the user rotate a key and GitGuardian re-detect it in the history in less than 1 hour?

**Context:** The `secret-dedup.true-positive-cooldown-hours` is set to 1 by default. If GitGuardian scans historical commits, a rotated key might still appear in the history.

**Options:**
1. Keep 1 hour (aggressive — fast detection of new exposures)
2. Increase to 6 hours (moderate — balances false positives with detection speed)
3. Increase to 24 hours (conservative — minimizes noise but delays new detection)
4. Make it per-tenant configurable

**Resolution Criteria:**
- [ ] Analyze GitGuardian historical scan behavior
- [ ] Survey team on acceptable detection delay
- [ ] Set final cooldown value and document rationale
- [ ] Make configurable per tenant if needed

---

### Q3: DLQ Notification

**Status:** 🔵 OPEN  
**Priority:** Medium  
**Owner:** (unassigned)

**Question:** How should the team be notified when there are alerts in the Dead Letter Queue? Email? Slack? Log alert?

**Context:** Alerts in the DLQ indicate processing failures that need investigation. The notification mechanism is not yet defined.

**Options:**
1. Slack notification via webhook to `#security-alerts` channel
2. Email to security team distribution list
3. CloudWatch alarm triggered by DLQ size metric
4. Combination of (1) + (3)

**Resolution Criteria:**
- [ ] Decide on notification channel(s)
- [ ] Implement notification in `DeadLetterQueueService`
- [ ] Configure alert threshold (e.g., DLQ size > 5)
- [ ] Document notification procedure

---

### Q4: Drools Rule Validation

**Status:** 🔵 OPEN  
**Priority:** High  
**Owner:** (unassigned)

**Question:** How should new `.drl` rules be validated before hot-reloading? Syntax validation only, or smoke tests too?

**Context:** The `DecisionEngine` supports hot-reloading of rules per tenant via `KieContainer`. Invalid rules could cause deployment failures or incorrect decisions.

**Options:**
1. Syntax validation only (compile `.drl` file and catch errors)
2. Syntax validation + smoke tests (run rules against sample alerts)
3. Syntax validation + smoke tests + staging environment deployment
4. Require admin API approval before hot-reload

**Resolution Criteria:**
- [ ] Implement rule validation pipeline in CI
- [ ] Add validation step to admin API for rule updates
- [ ] Document validation requirements for rule authors
- [ ] Test hot-reload with valid and invalid rules

---

### Q5: AWS Credentials Scope for Rotation

**Status:** 🔵 OPEN  
**Priority:** High  
**Owner:** (unassigned)

**Question:** What are the minimum IAM permissions required for the rotation service? `iam:CreateAccessKey`, `iam:DeactivateMFADevice`, `sts:GetCallerIdentity`?

**Context:** The rotation service needs specific AWS IAM permissions to perform credential rotation. Following the principle of least privilege, we need to identify the exact minimum set.

**Required Permissions (Tentative):**

| Permission | Purpose |
|------------|---------|
| `iam:CreateAccessKey` | Create new access key for user |
| `iam:DeleteAccessKey` | Delete old or failed access key |
| `iam:DeactivateAccessKey` | Deactivate old access key before rotation |
| `iam:GetAccessKeyLastUsed` | Verify new key works (optional) |
| `iam:ListAccessKeys` | List existing access keys |
| `iam:DeactivateMFADevice` | Deactivate MFA device before rotation |
| `iam:ListMFADevices` | List MFA devices |
| `sts:GetCallerIdentity` | Verify new credentials work |
| `secretsmanager:GetSecretValue` | Read existing credentials from SM |
| `secretsmanager:PutSecretValue` | Store new credentials in SM |
| `secretsmanager:DeleteSecret` | Clean up failed rotation in SM |

**Resolution Criteria:**
- [ ] Define IAM policy with minimum permissions
- [ ] Test rotation with restricted permissions
- [ ] Document required permissions in runbook
- [ ] Create IAM policy file for Terraform

---

### Q6: Encryption Key Rotation for AES-256

**Status:** 🔵 OPEN  
**Priority:** Medium  
**Owner:** (unassigned)

**Question:** How should the `ENCRYPTION_MASTER_KEY` be rotated without losing existing data in PostgreSQL? Is this necessary in Phase 1?

**Context:** If the master key is lost or compromised, all encrypted data becomes inaccessible. However, rotating the key requires re-encrypting all existing records.

**Options:**
1. Defer to Phase 3 (production) — not needed for POC
2. Implement versioned encryption (store key ID with encrypted data)
3. Use AWS KMS for key management (supports key rotation transparently)
4. Implement key wrapping (encrypt data key with master key, store both)

**Resolution Criteria:**
- [ ] Decide whether key rotation is needed in Phase 1
- [ ] If yes: implement versioned encryption scheme
- [ ] If no: document decision and timeline for Phase 2/3
- [ ] Add key rotation procedure to runbook

---

### Q7: Tenant Onboarding

**Status:** 🔵 OPEN  
**Priority:** Medium  
**Owner:** (unassigned)

**Question:** How should tenant credentials be provisioned initially? Manual via admin API? Automated via setup wizard?

**Context:** When a new tenant joins, their cloud provider credentials need to be stored in the system so the verification engine can verify exposed secrets. The onboarding process is not yet defined.

**Options:**
1. Manual via admin API (`POST /api/v1/admin/tenants`) — operator enters credentials
2. Automated via setup wizard — tenant provides credentials through a secure web form
3. Automated via Terraform — infrastructure code provisions credentials
4. Hybrid: manual for Phase 1, automated for Phase 2+

**Resolution Criteria:**
- [ ] Decide on onboarding method
- [ ] Implement admin API endpoint if manual
- [ ] Create onboarding documentation for operators
- [ ] Add tenant validation checks (verify credentials on creation)

---

## Resolution Status Summary

| # | Question | Status | Priority |
|---|----------|--------|----------|
| 1 | GitGuardian signature format | 🔵 Open | High |
| 2 | Cooldown TTL for true_positive | 🔵 Open | Medium |
| 3 | DLQ notification mechanism | 🔵 Open | Medium |
| 4 | Drools rule validation | 🔵 Open | High |
| 5 | AWS credentials scope | 🔵 Open | High |
| 6 | AES-256 key rotation | 🔵 Open | Medium |
| 7 | Tenant onboarding | 🔵 Open | Medium |

**Status Legend:**
- 🔵 Open — Not yet addressed
- 🟡 In Progress — Being worked on
- 🟢 Resolved — Answered and documented
- ❌ Superseded — Question no longer relevant
