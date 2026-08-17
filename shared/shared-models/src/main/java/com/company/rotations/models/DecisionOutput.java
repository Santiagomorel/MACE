package com.company.rotations.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "decision_outputs")
public class DecisionOutput {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "alert_id", nullable = false)
    private UUID alertId;

    @Column(name = "decision", nullable = false)
    private String decision;

    @Column(name = "severity")
    private String severity;

    @Column(name = "reason")
    private String reason;

    @Column(name = "playbook_name")
    private String playbookName;

    @Column(name = "calculated_via")
    private String calculatedVia;

    @Column(name = "playbook_compliance_tags")
    private String playbookComplianceTags;

    @Column(name = "evaluated_rule_version")
    private Integer evaluatedRuleVersion;

    public DecisionOutput() {}

    public DecisionOutput(UUID alertId, String decision, String severity,
                          String reason, String playbookName) {
        this.alertId = alertId;
        this.decision = decision;
        this.severity = severity;
        this.reason = reason;
        this.playbookName = playbookName;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getAlertId() { return alertId; }
    public void setAlertId(UUID alertId) { this.alertId = alertId; }

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getPlaybookName() { return playbookName; }
    public void setPlaybookName(String playbookName) { this.playbookName = playbookName; }

    public String getCalculatedVia() { return calculatedVia; }
    public void setCalculatedVia(String calculatedVia) { this.calculatedVia = calculatedVia; }

    public String getPlaybookComplianceTags() { return playbookComplianceTags; }
    public void setPlaybookComplianceTags(String playbookComplianceTags) { this.playbookComplianceTags = playbookComplianceTags; }

    public Integer getEvaluatedRuleVersion() { return evaluatedRuleVersion; }
    public void setEvaluatedRuleVersion(Integer evaluatedRuleVersion) { this.evaluatedRuleVersion = evaluatedRuleVersion; }
}
