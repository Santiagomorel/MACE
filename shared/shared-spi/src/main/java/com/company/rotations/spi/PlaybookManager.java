package com.company.rotations.spi;

import java.util.List;

public interface PlaybookManager {
    String VERSION = "1.0.0";

    Playbook loadPlaybook(String credentialType);

    List<String> getPlaybookSteps(String credentialType);

    default String getVersion() {
        return VERSION;
    }
}
