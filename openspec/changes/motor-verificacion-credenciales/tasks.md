## 1. Module Setup and Configuration

- [ ] [R2] 1.1 Create package organization for verification module (provider, validator, permission-enumerator, account-mapper, cache)
- [ ] [R2] 1.2 Add cloud SDK dependencies (AWS SDK v2) to build configuration
- [ ] [R2] 1.3 Configure circuit breaker framework (Resilience4j or equivalent) in application properties
- [ ] [R2] 1.4 Set up cache layer with TTL of 5 minutes for verification results
- [ ] [R2] 1.5 Define data models: CredentialAlert, ProviderType, VerificationResult, PermissionMatrix

## 2. Provider Detection

- [ ] [R2] 2.1 Implement provider detector that extracts provider from GitGuardian alert payload
- [ ] [R2] 2.2 Implement heuristic provider detection as fallback (AKIA → AWS, eyJ → Azure, AIzaSy → GCP)
- [ ] [R2] 2.3 Write unit tests for all prefix-based provider detection heuristics

## 3. Account Mapping

- [ ] [R2] 3.1 Implement account mapper that uses GitGuardian account_hint when available
- [ ] [R2] 3.2 Implement iterative account lookup fallback when no hint is available
- [ ] [R2] 3.3 Add UNKNOWN status marker for unmapped accounts
- [ ] [R2] 3.4 Write unit tests for account mapping scenarios

## 4. Credential Validator with Concurrent Fan-Out

- [ ] [R2] 4.1 Create credential validator orchestration service that handles fan-out to multiple providers
- [ ] [R2] 4.2 Implement AWS verification using admin-read-only credentials (STS GetCallerIdentity)
- [ ] [R2] 4.3 Add stub classes for Azure AD and GCP verification (deferred, not in scope for R1)
- [ ] [R2] 4.4 Create provider-specific circuit breakers per cloud provider
- [ ] [R2] 4.5 Implement expired/invalid credential detection using STS response errors

## 5. Permission Enumeration (AWS)

- [ ] [R2] 5.1 Implement GetCallerIdentity step to validate identity and retrieve ARN
- [ ] [R2] 5.2 Implement ListAttachedUserPolicies call to retrieve all attached policies
- [ ] [R2] 5.3 Implement GetUserPolicy for inline policy extraction
- [ ] [R2] 5.4 Build ALLOW - DENY permission matrix by parsing All JSON Statement arrays
- [ ] [R2] 5.5 Apply DENY precedence over ALLOW per action in the resulting matrix
- [ ] [R2] 5.6 Handle empty permission set case (no active statements)

## 6. Result Construction and Cache Integration

- [ ] [R2] 6.1 Construct VerificationResult output with required format: { account_id, identity_arn, action-matrix: Set<String>, last_used_date }
- [ ] [R2] 6.2 Implement FAILED verification output format: { account_id, identity_arn, status: INVALID, last_used_date=never }
- [ ] [R2] 6.3 Integrate cache layer into verification flow (check-before-fetch pattern with 5-min TTL)
- [ ] [R2] 6.4 Handle cache miss and TTL expiration scenarios

## 7. Rate Limit Management

- [ ] [R2] 7.1 Implement exponential backoff retry logic for HTTP 429 responses from cloud APIs
- [ ] [R2] 7.2 Configure maximum retry attempts threshold
- [ ] [R2] 7.3 Return RATE_LIMITED status when all retries are exhausted without success
- [ ] [R2] 7.4 Ensure rate limiting does not block concurrent provider verifications

## 8. Input and Output Integration

- [ ] [R2] 8.1 Create input adapter that receives normalized GitGuardian alert format
- [ ] [R2] 8.2 Wire verification module output to Drools engine via action_matrix, blast_radius estimated, last_used_date fields
- [ ] [R2] 8.3 Add structured logging for all steps of the verification flow (arrival, validation, enumeration, output)

## 9. Testing and Validation

- [ ] [R2] 9.1 Write unit tests for provider detection (all heuristics + GitGuardian hint paths)
- [ ] [R2] 9.2 Write unit tests for account mapping (hint-based, iterative fallback, unknown marker)
- [ ] [R2] 9.3 Write unit tests for credential validation flow (valid, expired, invalid scenarios)
- [ ] [R2] 9.4 Write unit tests for permission enumeration (ALLOW minus DENY, precedence, empty sets)
- [ ] [R2] 9.5 Write integration test for end-to-end verification pipeline with mocked STS responses

## 10. Documentation and Deployment Preparation

- [ ] [R2] 10.1 Add module-level documentation in the verification package
- [ ] [R2] 10.2 Document open questions section (vault integration, account count per client, Azure/GCP deferred)
- [ ] [R2] 10.3 Verify output format compatibility with Drools risk evaluation engine
