package com.springagent.diagnosis.domain.dto.stream;

public record StreamError(String code, String message, boolean retryable) {
}
