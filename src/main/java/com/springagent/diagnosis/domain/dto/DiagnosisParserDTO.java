package com.springagent.diagnosis.domain.dto;

import java.util.UUID;
import lombok.Data;

@Data
public class DiagnosisParserDTO {
    private UUID conversationId;
    private String input;
    private String fileName;
    private String mediaType;
    private byte[] content;
}
