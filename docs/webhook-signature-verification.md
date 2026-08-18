# Webhook Signature Verification

This document describes the HMAC-SHA256 webhook signature verification process used by the `alert-integrator` module to authenticate incoming alerts from providers like GitGuardian.

---

## Overview

All webhook POST requests to `/api/v1/alerts` must include a valid cryptographic signature. The system validates the signature using HMAC-SHA256 with a shared secret configured per provider.

```
Provider ──▶ POST /api/v1/alerts ──▶ SignatureValidator ──▶ IP Whitelist ──▶ Pipeline
             (with X-Signature header)
```

### Authentication Layers

The webhook endpoint enforces **two** authentication layers:

| Layer | Mechanism | Configuration |
|-------|-----------|---------------|
| **Signature** | HMAC-SHA256 of request body | `app.providers.{provider}.shared-secret` |
| **IP Whitelist** | CIDR-based IP filtering | `app.providers.{provider}.allowed-ips` |

Both must pass for the request to be processed.

---

## GitGuardian Signature Verification

### How GitGuardian Signs Webhooks

GitGuardian signs each webhook using HMAC-SHA256:

```
signature = HMAC-SHA256(body, shared_secret)
```

The signature is sent in the header defined by `app.providers.gitguardian.signature-header` (default: `X-GitGuardian-Signature`).

### Configuration

In `application.yml` (or via environment variables):

```yaml
app:
  providers:
    gitguardian:
      shared-secret: ${GITGUARDIAN_WEBHOOK_SECRET}
      signature-header: X-GitGuardian-Signature
      allowed-ips: 72.14.199.0/24, 184.172.192.0/24
```

Environment variables:
- `GITGUARDIAN_WEBHOOK_SECRET` — The shared secret for HMAC-SHA256 signing
- `ADMIN_API_KEY` — API key for `/api/v1/admin/**` endpoints

### Verification Flow

```
1. Receive POST /api/v1/alerts with body and X-GitGuardian-Signature header
2. Check if shared-secret is configured and not the default "changeme"
   ├─ Not configured → Skip validation (dev mode), log warning
   └─ Configured → Continue
3. Compute HMAC-SHA256 of request body using the shared secret
4. Compare computed signature with received signature (constant-time comparison)
5. Check source IP against allowed-ips CIDR list
6. If both pass → process alert
7. If either fails → return 401 Unauthorized
```

### SignatureValidator Implementation

```java
@Component
public class SignatureValidator {

    private static final String ALGORITHM = "HmacSHA256";
    private final String sharedSecret;
    private final String signatureHeader;

    public boolean isValid(String payload, String signature, String source) {
        // 1. Check for missing signature
        if (signature == null || signature.isBlank()) {
            return false;
        }

        // 2. Check if secret is configured (skip validation in dev)
        if (sharedSecret == null || sharedSecret.isBlank() 
            || "changeme".equals(sharedSecret)) {
            return true;  // Dev mode: skip validation
        }

        // 3. Compute HMAC-SHA256
        Mac mac = Mac.getInstance(ALGORITHM);
        SecretKeySpec keySpec = new SecretKeySpec(
            sharedSecret.getBytes(StandardCharsets.UTF_8), ALGORITHM
        );
        mac.init(keySpec);
        byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        String computedSignature = HexFormat.of().formatHex(hmacBytes);

        // 4. Constant-time comparison (prevent timing attacks)
        boolean valid = constantTimeEquals(computedSignature, signature.toLowerCase());
        return valid;
    }
}
```

### Response Codes

| Status | Body | When |
|--------|------|------|
| `200 OK` | `{"status": "accepted"}` | Valid signature + IP whitelisted |
| `401 Unauthorized` | `{"error": "INVALID_SIGNATURE"}` | HMAC mismatch |
| `401 Unauthorized` | `{"error": "MISSING_SIGNATURE"}` | No `X-GitGuardian-Signature` header |
| `403 Forbidden` | `{"error": "IP_FORBIDDEN"}` | Source IP not in whitelist |

---

## Adding a New Provider

### Step 1: Create Adapter

```java
@Component
public class SnykAdapter implements AlertAdapter {
    @Override
    public String getProviderName() {
        return "snyk";
    }

    @Override
    public GenericAlertModel toGenericAlert(Map<String, Object> rawPayload) {
        // Map Snyk payload → GenericAlertModel
    }
}
```

### Step 2: Configure Provider Secrets

Add to `application.yml`:

```yaml
app:
  providers:
    snyk:
      shared-secret: ${SNYK_WEBHOOK_SECRET}
      signature-header: X-Snyk-Signature
      allowed-ips: ${SNYK_ALLOWED_IPS}
```

### Step 3: Update SignatureValidator

The `SignatureValidator` is provider-agnostic and uses the configured `shared-secret` for HMAC computation. Each provider just needs its own secret and signature header name.

---

## Security Best Practices

1. **Always use environment variables** for shared secrets — never hardcode them
2. **Configure IP whitelists** — even with valid signatures, restrict source IPs
3. **Use constant-time comparison** — prevents timing side-channel attacks
4. **Log validation failures** — but never log the shared secret or the received signature in full
5. **Rotate secrets** — update `shared-secret` periodically and update the provider configuration

---

## Testing

### Test with curl

```bash
# Compute HMAC-SHA256 signature
SECRET="my-shared-secret"
PAYLOAD='{"providerName":"gitguardian","credentialType":"AWS_ACCESS_KEY","tenantId":"tenant-123"}'
SIG=$(echo -n "$PAYLOAD" | openssl dgst -sha256 -hmac "$SECRET" | awk '{print $2}')

# Send webhook
curl -X POST http://localhost:8082/api/v1/alerts \
  -H "Content-Type: application/json" \
  -H "X-GitGuardian-Signature: $SIG" \
  -d "$PAYLOAD"
```

### Test with unit tests

```java
@SpringBootTest
class SignatureValidatorTest {

    @Test
    void validSignature_shouldReturnTrue() {
        var validator = new SignatureValidator("test-secret", "X-Signature");
        String payload = "{\"test\":\"data\"}";
        String signature = computeHmac(payload, "test-secret");
        assertTrue(validator.isValid(payload, signature, "test"));
    }

    @Test
    void invalidSignature_shouldReturnFalse() {
        var validator = new SignatureValidator("test-secret", "X-Signature");
        String payload = "{\"test\":\"data\"}";
        assertFalse(validator.isValid(payload, "invalid-signature", "test"));
    }

    @Test
    void missingSignature_shouldReturnFalse() {
        var validator = new SignatureValidator("test-secret", "X-Signature");
        assertFalse(validator.isValid("{\"test\":\"data\"}", null, "test"));
    }
}
```
