package com.springagent.diagnosis.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class DiagnosisRequest {
    private UUID conversationId;
    @NotBlank(message = "诊断内容不能为空")
    private String input;
}
