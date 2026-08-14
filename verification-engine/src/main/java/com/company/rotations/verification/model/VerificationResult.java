package com.company.rotations.verification.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Set;

public class VerificationResult {

    private String accountId;
    private String identityArn;
    private VerificationStatus status;
    private Set<String> actionMatrix;
    private String lastUsedDate;
    private Instant verifiedAt;
    private String reason;

    public VerificationResult() {}

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    @JsonProperty("identity_arn")
    public String getIdentityArn() { return identityArn; }
    public void setIdentityArn(String identityArn) { this.identityArn = identityArn; }

    public VerificationStatus getStatus() { return status; }
    public void setStatus(VerificationStatus status) { this.status = status; }

    @JsonProperty("action_matrix")
    public Set<String> getActionMatrix() { return actionMatrix; }
    public void setActionMatrix(Set<String> actionMatrix) { this.actionMatrix = actionMatrix; }

    @JsonProperty("last_used_date")
    public String getLastUsedDate() { return lastUsedDate; }
    public void setLastUsedDate(String lastUsedDate) { this.lastUsedDate = lastUsedDate; }

    @JsonProperty("verified_at")
    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public static VerificationResult success(String accountId, String identityArn, Set<String> actionMatrix, String lastUsedDate) {
        VerificationResult result = new VerificationResult();
        result.setAccountId(accountId);
        result.setIdentityArn(identityArn);
        result.setStatus(VerificationStatus.VERIFIED);
        result.setActionMatrix(actionMatrix);
        result.setLastUsedDate(lastUsedDate);
        result.setVerifiedAt(Instant.now());
        return result;
    }

    public static VerificationResult failed(String accountId, String identityArn, String reason) {
        VerificationResult result = new VerificationResult();
        result.setAccountId(accountId);
        result.setIdentityArn(identityArn);
        result.setStatus(VerificationStatus.INVALID);
        result.setLastUsedDate("never");
        result.setReason(reason);
        result.setVerifiedAt(Instant.now());
        return result;
    }

    public static VerificationResult rateLimited(String accountId, String identityArn) {
        VerificationResult result = new VerificationResult();
        result.setAccountId(accountId);
        result.setIdentityArn(identityArn);
        result.setStatus(VerificationStatus.RATE_LIMITED);
        result.setLastUsedDate("unknown");
        result.setVerifiedAt(Instant.now());
        return result;
    }

    public static VerificationResult unknownAccount(String credentialId) {
        VerificationResult result = new VerificationResult();
        result.setAccountId("UNKNOWN");
        result.setIdentityArn(credentialId);
        result.setStatus(VerificationStatus.UNKNOWN);
        result.setLastUsedDate("unknown");
        result.setReason("Account could not be mapped to any known client account");
        result.setVerifiedAt(Instant.now());
        return result;
    }
}
