package com.company.rotations.verification.severity;

import java.util.Map;
import java.util.Optional;

public class SeverityRuleEngine {

    private final Map<String, String> tenantSeverityFloors;
    private final String defaultFloor;

    public enum Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public SeverityRuleEngine(Map<String, String> tenantSeverityFloors, String defaultFloor) {
        this.tenantSeverityFloors = tenantSeverityFloors != null ? Map.copyOf(tenantSeverityFloors) : Map.of();
        this.defaultFloor = defaultFloor != null ? defaultFloor : "MEDIUM";
    }

    public Severity applyFloor(String tenantId, Severity calculatedSeverity) {
        if (tenantId == null || tenantId.isBlank()) {
            return maxSeverity(calculatedSeverity, parseSeverity(defaultFloor));
        }

        Optional<String> floor = getTenantFloor(tenantId);
        if (floor.isEmpty()) {
            return maxSeverity(calculatedSeverity, parseSeverity(defaultFloor));
        }

        Severity floorSeverity = parseSeverity(floor.get());
        return maxSeverity(calculatedSeverity, floorSeverity);
    }

    public Optional<String> getTenantFloor(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return Optional.empty();
        }
        String floor = tenantSeverityFloors.get(tenantId.toLowerCase());
        return floor != null ? Optional.of(floor) : Optional.empty();
    }

    public String getDefaultFloor() {
        return defaultFloor;
    }

    private Severity parseSeverity(String severity) {
        if (severity == null) {
            return parseSeverity(defaultFloor);
        }
        try {
            return Severity.valueOf(severity.toUpperCase());
        } catch (IllegalArgumentException e) {
            return parseSeverity(defaultFloor);
        }
    }

    private Severity maxSeverity(Severity a, Severity b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    public boolean hasTenantRule(String tenantId) {
        return tenantId != null && tenantSeverityFloors.containsKey(tenantId.toLowerCase());
    }

    public int getTenantCount() {
        return tenantSeverityFloors.size();
    }
}
