package com.company.rotations.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;

public class GenericAlertModel {

    private String eventId;
    private String source;
    private String sourceEventId;
    private DetectedSecret detectedSecret;
    private AlertContext context;
    private String providerSeverity;
    private DetectorState detectorState;
    private Instant receivedAt;
    private Map<String, Object> rawPayload;

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getSourceEventId() { return sourceEventId; }
    public void setSourceEventId(String sourceEventId) { this.sourceEventId = sourceEventId; }

    public DetectedSecret getDetectedSecret() { return detectedSecret; }
    public void setDetectedSecret(DetectedSecret detectedSecret) { this.detectedSecret = detectedSecret; }

    public AlertContext getContext() { return context; }
    public void setContext(AlertContext context) { this.context = context; }

    public String getProviderSeverity() { return providerSeverity; }
    public void setProviderSeverity(String providerSeverity) { this.providerSeverity = providerSeverity; }

    public DetectorState getDetectorState() { return detectorState; }
    public void setDetectorState(DetectorState detectorState) { this.detectorState = detectorState; }

    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }

    public Map<String, Object> getRawPayload() { return rawPayload; }
    public void setRawPayload(Map<String, Object> rawPayload) { this.rawPayload = rawPayload; }

    @JsonProperty("value_hash")
    public String getValueHash() {
        return detectedSecret != null ? detectedSecret.getValueHash() : null;
    }

    @JsonProperty("value_hash")
    public void setValueHash(String valueHash) {
        if (valueHash != null) {
            if (this.detectedSecret == null) {
                this.detectedSecret = new DetectedSecret();
            }
            this.detectedSecret.setValueHash(valueHash);
        }
    }

    public static class DetectedSecret {
        private String type;
        private String valueHash;
        private String pattern;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        @JsonProperty("value_hash")
        public String getValueHash() { return valueHash; }
        public void setValueHash(String valueHash) { this.valueHash = valueHash; }

        public String getPattern() { return pattern; }
        public void setPattern(String pattern) { this.pattern = pattern; }
    }

    public static class AlertContext {
        private String repository;
        private String file;
        private String commit;
        private Integer line;
        private String visibility;
        private Instant foundAt;

        public String getRepository() { return repository; }
        public void setRepository(String repository) { this.repository = repository; }

        public String getFile() { return file; }
        public void setFile(String file) { this.file = file; }

        public String getCommit() { return commit; }
        public void setCommit(String commit) { this.commit = commit; }

        public Integer getLine() { return line; }
        public void setLine(Integer line) { this.line = line; }

        public String getVisibility() { return visibility; }
        public void setVisibility(String visibility) { this.visibility = visibility; }

        public Instant getFoundAt() { return foundAt; }
        public void setFoundAt(Instant foundAt) { this.foundAt = foundAt; }
    }

    public static class DetectorState {
        private boolean isNew;
        private boolean previouslyFlagged;
        private int flagCount;

        public boolean isNew() { return isNew; }
        public void setIsNew(boolean isNew) { this.isNew = isNew; }

        public boolean isPreviouslyFlagged() { return previouslyFlagged; }
        public void setPreviouslyFlagged(boolean previouslyFlagged) { this.previouslyFlagged = previouslyFlagged; }

        public int getFlagCount() { return flagCount; }
        public void setFlagCount(int flagCount) { this.flagCount = flagCount; }
    }
}
