package com.springagent.diagnosis.domain.dto.response;

import com.springagent.diagnosis.domain.constant.RiskSeverity;
import java.util.UUID;

public record CompatibilityIssueResponse(
        UUID id,
        String component,
        String issueType,
        RiskSeverity severity,
        String currentVersion,
        String targetVersion,
        String symptom,
        String rootCause,
        Boolean confirmed,
        Integer sortOrder
) {
}
