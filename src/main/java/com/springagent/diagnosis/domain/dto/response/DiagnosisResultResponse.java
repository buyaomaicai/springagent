package com.springagent.diagnosis.domain.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * 提供给前端展示的一次完整结构化诊断结果。
 */
public record DiagnosisResultResponse(
        UUID diagnosisId,
        String summary,
        UpgradeTargetResponse target,
        List<DiagnosisRiskResponse> risks,
        List<CompatibilityIssueResponse> compatibilityIssues,
        List<ModificationSuggestionResponse> suggestions,
        List<UpgradePlanStepResponse> planSteps,
        List<KnowledgeEvidenceResponse> evidence
) {
    public DiagnosisResultResponse {
        risks = List.copyOf(risks);
        compatibilityIssues = List.copyOf(compatibilityIssues);
        suggestions = List.copyOf(suggestions);
        planSteps = List.copyOf(planSteps);
        evidence = List.copyOf(evidence);
    }
}
