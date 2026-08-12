package com.company.rotations.alerting.config;

public class ValidationException extends RuntimeException {

    private final String field;
    private final String message;

    public ValidationException(String field, String message) {
        super(message);
        this.field = field;
        this.message = message;
    }

    public ValidationException(String message) {
        super(message);
        this.field = null;
        this.message = message;
    }

    public String getField() {
        return field;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
