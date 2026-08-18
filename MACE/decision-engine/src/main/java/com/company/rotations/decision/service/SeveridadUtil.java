package com.company.rotations.decision.service;

import com.company.rotations.models.Severidad;

public class SeveridadUtil {

    public static Severidad max(Severidad a, Severidad b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.getRank() >= b.getRank() ? a : b;
    }

    public static int toSalience(Severidad severidad) {
        return switch (severidad) {
            case CRITICO -> 100;
            case ALTO -> 80;
            case MEDIA -> 60;
            case BAJO -> 40;
        };
    }

    public static Severidad fromSalience(int salience) {
        return switch (salience) {
            case 100 -> Severidad.CRITICO;
            case 80 -> Severidad.ALTO;
            case 60 -> Severidad.MEDIA;
            case 40 -> Severidad.BAJO;
            default -> throw new IllegalArgumentException("Unknown salience: " + salience);
        };
    }

    private SeveridadUtil() {}
}
