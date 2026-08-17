package com.springagent.diagnosis.domain.dto.result;


import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record DiagnosisResult(
        @NotBlank String summary,
        @NotNull @Valid UpgradeTarget target,
        @NotNull List<@Valid RiskItem> risks,
        @NotNull List<@Valid CompatibilityFinding> compatibilityIssues,
        @NotNull List<@Valid SuggestedModification> suggestions,
        @NotNull List<@Valid UpgradePlanStepResult> planSteps
) {
    public DiagnosisResult {
        risks = risks == null ? null : List.copyOf(risks);
        compatibilityIssues = compatibilityIssues == null
                ? null : List.copyOf(compatibilityIssues);
        suggestions = suggestions == null ? null : List.copyOf(suggestions);
        planSteps = planSteps == null ? null : List.copyOf(planSteps);
    }
}