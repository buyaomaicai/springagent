package com.springagent.diagnosis.domain.dto.result;

import com.springagent.diagnosis.domain.constant.UpgradePhase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record UpgradePlanStepResult(
        @NotNull @Positive Integer sequenceNo,
        @NotNull UpgradePhase phase,
        @NotBlank String title,
        @NotBlank String description,
        @NotNull List<String> prerequisites,
        String verification,
        String rollbackAction,
        String estimatedEffort
) {
}
