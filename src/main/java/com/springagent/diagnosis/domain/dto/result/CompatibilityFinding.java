package com.springagent.diagnosis.domain.dto.result;

import com.springagent.diagnosis.domain.constant.RiskSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CompatibilityFinding(
        @NotBlank String component,
        @NotBlank String issueType,
        @NotNull RiskSeverity severity,
        String currentVersion,
        String targetVersion,
        String symptom,
        @NotBlank String rootCause,
        @NotNull Boolean confirmed
) {
}