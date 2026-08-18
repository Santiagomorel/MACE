package com.company.rotations.verification.model;

import java.util.*;

public class PermissionMatrix {

    private final Set<String> allowedActions = new LinkedHashSet<>();
    private final Set<String> deniedActions = new LinkedHashSet<>();
    private final Set<String> effectiveActions = new LinkedHashSet<>();

    public void addAllowed(String action) {
        if (action != null && !action.isBlank()) {
            allowedActions.add(action);
        }
    }

    public void addDenied(String action) {
        if (action != null && !action.isBlank()) {
            deniedActions.add(action);
            allowedActions.remove(action);
        }
    }

    public Set<String> getAllowedActions() {
        return Collections.unmodifiableSet(allowedActions);
    }

    public Set<String> getDeniedActions() {
        return Collections.unmodifiableSet(deniedActions);
    }

    public Set<String> getEffectiveActions() {
        if (effectiveActions.isEmpty()) {
            effectiveActions.addAll(allowedActions);
        }
        return Collections.unmodifiableSet(effectiveActions);
    }

    public boolean isEmpty() {
        return allowedActions.isEmpty() && deniedActions.isEmpty();
    }

    public int size() {
        return effectiveActions.size();
    }

    public void clear() {
        allowedActions.clear();
        deniedActions.clear();
        effectiveActions.clear();
    }
}
