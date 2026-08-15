package com.springagent.diagnosis.domain.dto;


import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;
@Data
public class DiagnosisRunDTO {
    private UUID id;

    private UUID conversationId;

    private String question;

    private String response;

    private String status;

    private JsonNode projectSnapshot;

    private String modelProvider;

    private String modelName;

    private String promptVersion;

    private String errorCode;

    private String errorDetail;

    private OffsetDateTime startedAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime completedAt;
}
