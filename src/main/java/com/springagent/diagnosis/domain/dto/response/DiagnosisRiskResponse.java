package com.springagent.diagnosis.domain.dto.response;

import com.springagent.diagnosis.domain.constant.RiskSeverity;
import java.util.UUID;

public record DiagnosisRiskResponse(
        UUID id,
        String category,
        RiskSeverity severity,
        Short likelihood,
        Short impact,
        String affectedComponent,
        String title,
        String description,
        String mitigation,
        Integer sortOrder
) {
}
