package com.springagent.diagnosis.domain.dto.result;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * 结构化结果中引用的一条知识证据。
 *
 * <p>输出契约只要求模型提供 {@code refIndex}（对应提示词中的 [REF-i] 编号）、
 * {@code sourceUrl} 和 {@code title}。其余字段由结构化服务在解析后用服务端
 * 检索到的真实文档覆盖填充，避免模型编造引用来源。</p>
 */
public record EvidenceReference(
        @NotNull @PositiveOrZero Integer refIndex,
        @NotBlank String sourceUrl,
        @NotBlank String title,
        String sourceType,
        String component,
        String versionRange,
        BigDecimal relevance,
        String excerpt
) {
}
