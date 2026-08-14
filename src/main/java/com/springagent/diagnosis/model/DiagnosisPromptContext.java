package com.springagent.diagnosis.model;

import com.springagent.diagnosis.entity.ChatMessage;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Optional;

public record DiagnosisPromptContext(
        String question,
        List<ChatMessage> history,
        List<Document> references,
        Optional<ProjectInput> projectInput
) {
}