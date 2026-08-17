package com.springagent.diagnosis.domain.dto.result;

import com.springagent.diagnosis.domain.constant.RiskSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RiskItem(
        @NotBlank String category,
        @NotNull RiskSeverity severity,
        @NotBlank String title,
        @NotBlank String description,
        String mitigation
) {
}