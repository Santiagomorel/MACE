package com.company.rotations.actionexecutor.domain;

import com.company.rotations.actionexecutor.audit.RotationTransitionDto;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RotationStateMachine {

    private static final Map<RotationState, Set<RotationState>> ALLOWED_TRANSITIONS = Map.of(
            RotationState.PENDING, Set.of(RotationState.ROTATING),
            RotationState.ROTATING, Set.of(RotationState.SUCCESS, RotationState.FAIL),
            RotationState.FAIL, Set.of(RotationState.ROTATING, RotationState.ESCALATE),
            RotationState.SUCCESS, Set.of(RotationState.ESCALATE),
            RotationState.ESCALATE, Set.of(),
            RotationState.TIMEOUT, Set.of()
    );

    private final String rotationId;
    private final UUID alertId;
    private final Map<UUID, RotationState> stateHistory;
    private final List<RotationTransitionDto> transitionLog;
    private final AtomicInteger attemptCounter;
    private Instant startTime;
    private final Map<String, Object> context;

    public RotationStateMachine(String rotationId, UUID alertId) {
        this.rotationId = rotationId;
        this.alertId = alertId;
        this.stateHistory = new ConcurrentHashMap<>();
        this.transitionLog = Collections.synchronizedList(new ArrayList<>());
        this.attemptCounter = new AtomicInteger(0);
        this.context = new ConcurrentHashMap<>();
        this.startTime = Instant.now();
        this.stateHistory.put(UUID.randomUUID(), RotationState.PENDING);
        this.context.put("currentState", RotationState.PENDING);
    }

    public synchronized RotationTransitionDto transitionTo(RotationState nextState, String reason) {
        RotationState currentState = getCurrentState();
        Instant now = Instant.now();
        long durationMs = java.time.Duration.between(getLastStateTime(), now).toMillis();

        validateTransition(currentState, nextState);

        RotationTransitionDto transition = new RotationTransitionDto(
                alertId,
                currentState != null ? currentState.name() : "NONE",
                nextState.name(),
                now,
                durationMs,
                reason,
                attemptCounter.get(),
                null
        );

        transitionLog.add(transition);
        stateHistory.put(UUID.randomUUID(), nextState);
        context.put("currentState", nextState);
        context.put("lastTransition", transition);

        return transition;
    }

    public synchronized RotationTransitionDto transitionTo(RotationState nextState, String reason,
                                                            String errorMessage) {
        RotationState currentState = getCurrentState();
        Instant now = Instant.now();
        long durationMs = java.time.Duration.between(getLastStateTime(), now).toMillis();

        validateTransition(currentState, nextState);

        RotationTransitionDto transition = new RotationTransitionDto(
                alertId,
                currentState != null ? currentState.name() : "NONE",
                nextState.name(),
                now,
                durationMs,
                reason,
                attemptCounter.get(),
                errorMessage
        );

        transitionLog.add(transition);
        stateHistory.put(UUID.randomUUID(), nextState);
        context.put("currentState", nextState);
        context.put("lastTransition", transition);

        return transition;
    }

    public synchronized RotationTransitionDto incrementAttempt() {
        RotationState currentState = getCurrentState();
        int nextAttempt = attemptCounter.incrementAndGet();

        context.put("currentAttempt", nextAttempt);

        RotationTransitionDto transition = new RotationTransitionDto(
                alertId,
                currentState != null ? currentState.name() : "NONE",
                currentState != null ? currentState.name() : "NONE",
                Instant.now(),
                0,
                "Attempt " + nextAttempt,
                nextAttempt,
                null
        );

        transitionLog.add(transition);
        return transition;
    }

    public synchronized RotationTransitionDto timeoutTransition() {
        RotationTransitionDto transition = new RotationTransitionDto(
                alertId,
                getCurrentState().name(),
                RotationState.TIMEOUT.name(),
                Instant.now(),
                java.time.Duration.between(startTime, Instant.now()).toMillis(),
                "Global timeout of 5 minutes exceeded",
                null,
                null
        );

        transitionLog.add(transition);
        stateHistory.put(UUID.randomUUID(), RotationState.TIMEOUT);
        context.put("currentState", RotationState.TIMEOUT);
        return transition;
    }

    public RotationState getCurrentState() {
        return (RotationState) context.getOrDefault("currentState", RotationState.PENDING);
    }

    private Instant getLastStateTime() {
        return (Instant) context.getOrDefault("lastStateTime", startTime);
    }

    public int getAttemptCount() {
        return attemptCounter.get();
    }

    public String getRotationId() {
        return rotationId;
    }

    public UUID getAlertId() {
        return alertId;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public List<RotationTransitionDto> getTransitionLog() {
        return Collections.unmodifiableList(transitionLog);
    }

    public Map<String, Object> getContext() {
        return Collections.unmodifiableMap(context);
    }

    public boolean isTerminalState() {
        return getCurrentState() == RotationState.ESCALATE ||
               getCurrentState() == RotationState.TIMEOUT;
    }

    public void setState(RotationState state) {
        context.put("currentState", state);
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    private void validateTransition(RotationState from, RotationState to) {
        if (from == null) return;
        Set<RotationState> allowed = ALLOWED_TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new InvalidTransitionException(
                    "Invalid transition from " + from.name() + " to " + to.name(),
                    from, to
            );
        }
    }

    public static boolean isTransitionValid(RotationState from, RotationState to) {
        if (from == null) return true;
        Set<RotationState> allowed = ALLOWED_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    public static Set<RotationState> getAllowedTransitions(RotationState state) {
        return ALLOWED_TRANSITIONS.getOrDefault(state, Set.of());
    }

    public static class InvalidTransitionException extends RuntimeException {
        private final RotationState fromState;
        private final RotationState toState;

        public InvalidTransitionException(String message, RotationState fromState, RotationState toState) {
            super(message);
            this.fromState = fromState;
            this.toState = toState;
        }

        public RotationState getFromState() { return fromState; }
        public RotationState getToState() { return toState; }
    }
}
