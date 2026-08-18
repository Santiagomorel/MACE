package com.company.rotations.decision.domain;

import com.company.rotations.models.Severidad;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CriticalityResult {

    private final Severidad calculatedCriticality;
    private final Severidad playbookFloor;
    private final Severidad clientRules;
    private final String rationale;
    private final String playbookId;
    private final String calculatedVia;

    public CriticalityResult(Severidad calculatedCriticality, Severidad playbookFloor,
                             Severidad clientRules, String rationale, String playbookId,
                             String calculatedVia) {
        this.calculatedCriticality = calculatedCriticality;
        this.playbookFloor = playbookFloor;
        this.clientRules = clientRules;
        this.rationale = rationale;
        this.playbookId = playbookId;
        this.calculatedVia = calculatedVia;
    }

    public Severidad getCalculatedCriticality() { return calculatedCriticality; }
    public Severidad getPlaybookFloor() { return playbookFloor; }
    public Severidad getClientRules() { return clientRules; }
    public String getRationale() { return rationale; }
    public String getPlaybookId() { return playbookId; }
    public String getCalculatedVia() { return calculatedVia; }
}
