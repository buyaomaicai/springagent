package com.springagent.diagnosis.domain.dto.response;

import com.springagent.diagnosis.domain.constant.ModificationActionType;
import com.springagent.diagnosis.domain.constant.SuggestionPriority;
import java.util.UUID;

public record ModificationSuggestionResponse(
        UUID id,
        SuggestionPriority priority,
        ModificationActionType actionType,
        String filePath,
        String title,
        String description,
        String beforeContent,
        String afterContent,
        String verification,
        String status,
        Integer sortOrder
) {
}
