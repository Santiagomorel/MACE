package com.company.rotations.alerting.config;

public class TechnicalException extends RuntimeException {

    private final String errorCode;

    public TechnicalException(String message) {
        super(message);
        this.errorCode = "TECHNICAL_ERROR";
    }

    public TechnicalException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "TECHNICAL_ERROR";
    }

    public TechnicalException(String message, Throwable cause, String errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
