package com.company.rotations.models;

public enum Severidad {
    BAJO(1),
    MEDIA(2),
    ALTO(3),
    CRITICO(4);

    private final int rank;

    Severidad(int rank) {
        this.rank = rank;
    }

    public int getRank() {
        return rank;
    }

    public static Severidad fromString(String value) {
        for (Severidad s : values()) {
            if (s.name().equalsIgnoreCase(value)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown Severidad: " + value);
    }
}
