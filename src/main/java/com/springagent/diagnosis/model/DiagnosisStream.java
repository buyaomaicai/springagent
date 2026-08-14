package com.springagent.diagnosis.model;

import java.util.Objects;
import java.util.UUID;
import reactor.core.publisher.Flux;

/**
 * 一次已经准备完成、可以被 Controller 输出为 SSE 的诊断流。
 *
 * @param diagnosisId 本次运行的唯一标识，用于查询日志和关联后续结构化结果
 * @param conversationId 实际使用的会话标识；创建新会话时它与请求中的空值不同
 * @param content 模型逐段产生的文本内容
 */
public record DiagnosisStream(
        UUID diagnosisId,
        UUID conversationId,
        Flux<String> content
) {

    public DiagnosisStream {
        Objects.requireNonNull(diagnosisId, "diagnosisId must not be null");
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        Objects.requireNonNull(content, "content must not be null");
    }
}
