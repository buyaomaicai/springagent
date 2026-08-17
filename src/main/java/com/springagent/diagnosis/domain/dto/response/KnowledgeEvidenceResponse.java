package com.springagent.diagnosis.domain.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 一次诊断引用的一条知识证据，供前端展示来源与置信度。
 */
public record KnowledgeEvidenceResponse(
        UUID id,
        String sourceType,
        String sourceUrl,
        String title,
        String component,
        String versionRange,
        String excerpt,
        BigDecimal relevance
) {
}
