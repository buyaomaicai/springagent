package com.springagent.diagnosis.domain.dto.request;

import lombok.Data;
import lombok.NonNull;

import java.util.UUID;

@Data
public class DiagnosisRequest {
    private UUID conversationId;
    @NonNull
    private String input;
}
