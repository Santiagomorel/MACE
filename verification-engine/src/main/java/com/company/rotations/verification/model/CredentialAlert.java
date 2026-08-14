package com.company.rotations.verification.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

public class CredentialAlert {

    private String eventId;
    private String source;
    private String accountHint;
    private String credentialValue;
    private String credentialValueHash;
    private String providerName;
    private AlertContext context;
    private Instant receivedAt;
    private Map<String, Object> rawPayload;

    public CredentialAlert() {}

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    @JsonProperty("account_hint")
    public String getAccountHint() { return accountHint; }
    public void setAccountHint(String accountHint) { this.accountHint = accountHint; }

    @JsonProperty("credential_value")
    public String getCredentialValue() { return credentialValue; }
    public void setCredentialValue(String credentialValue) { this.credentialValue = credentialValue; }

    @JsonProperty("credential_value_hash")
    public String getCredentialValueHash() { return credentialValueHash; }
    public void setCredentialValueHash(String credentialValueHash) { this.credentialValueHash = credentialValueHash; }

    @JsonProperty("provider_name")
    public String getProviderName() { return providerName; }
    public void setProviderName(String providerName) { this.providerName = providerName; }

    public AlertContext getContext() { return context; }
    public void setContext(AlertContext context) { this.context = context; }

    @JsonProperty("received_at")
    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }

    @JsonProperty("raw_payload")
    public Map<String, Object> getRawPayload() { return rawPayload; }
    public void setRawPayload(Map<String, Object> rawPayload) { this.rawPayload = rawPayload; }

    public static class AlertContext {
        private String repository;
        private String file;
        private String commit;
        private String visibility;

        public String getRepository() { return repository; }
        public void setRepository(String repository) { this.repository = repository; }

        public String getFile() { return file; }
        public void setFile(String file) { this.file = file; }

        public String getCommit() { return commit; }
        public void setCommit(String commit) { this.commit = commit; }

        public String getVisibility() { return visibility; }
        public void setVisibility(String visibility) { this.visibility = visibility; }
    }
}
