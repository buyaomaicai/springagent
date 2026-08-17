package com.springagent.diagnosis.domain.dto.result;

import com.springagent.diagnosis.domain.constant.ModificationActionType;
import com.springagent.diagnosis.domain.constant.SuggestionPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SuggestedModification(
        @NotNull SuggestionPriority priority,
        @NotNull ModificationActionType actionType,
        String filePath,
        @NotBlank String title,
        @NotBlank String description,
        String beforeContent,
        String afterContent,
        String verification
) {
}