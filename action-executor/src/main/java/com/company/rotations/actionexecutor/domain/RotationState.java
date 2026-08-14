package com.company.rotations.actionexecutor.domain;

public enum RotationState {
    PENDING,
    ROTATING,
    SUCCESS,
    FAIL,
    ESCALATE,
    TIMEOUT
}
