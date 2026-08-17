package com.springagent.diagnosis.domain.dto.response;

import com.springagent.diagnosis.domain.constant.UpgradePhase;
import java.util.List;
import java.util.UUID;

public record UpgradePlanStepResponse(
        UUID id,
        Integer sequenceNo,
        UpgradePhase phase,
        String title,
        String description,
        List<String> prerequisites,
        String verification,
        String rollbackAction,
        String estimatedEffort,
        String status
) {
    public UpgradePlanStepResponse {
        prerequisites = List.copyOf(prerequisites);
    }
}
