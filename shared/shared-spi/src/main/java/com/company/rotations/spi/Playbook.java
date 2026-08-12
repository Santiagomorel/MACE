package com.company.rotations.spi;

import java.util.List;

public interface Playbook {
    String getName();
    List<String> getSteps();
    String getCondition();
}
