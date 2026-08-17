package com.springagent.diagnosis.domain.dto.response;

import com.springagent.diagnosis.model.DiagnosisRunStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 历史列表使用的轻量诊断摘要，不包含模型原始输出和项目完整快照。
 */
public record DiagnosisRunSummaryResponse(
        UUID diagnosisId,
        UUID conversationId,
        String question,
        DiagnosisRunStatus status,
        String summary,
        UpgradeTargetResponse target,
        String errorCode,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        OffsetDateTime createdAt
) {
}
