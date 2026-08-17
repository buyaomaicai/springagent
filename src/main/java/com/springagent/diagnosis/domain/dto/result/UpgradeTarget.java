package com.springagent.diagnosis.domain.dto.result;

import jakarta.validation.constraints.NotBlank;

public record UpgradeTarget(
        @NotBlank String javaVersion,
        @NotBlank String springBootVersion
) {
}