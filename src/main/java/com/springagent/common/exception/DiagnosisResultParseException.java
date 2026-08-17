package com.springagent.common.exception;

public class DiagnosisResultParseException extends RuntimeException {

    public DiagnosisResultParseException(String message) {
        super(message);
    }

    public DiagnosisResultParseException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}