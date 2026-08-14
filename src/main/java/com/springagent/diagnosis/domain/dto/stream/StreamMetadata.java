package com.springagent.diagnosis.domain.dto.stream;

import java.util.UUID;

/**
 * SSE 流建立后的第一条元数据。
 *
 * @param protocolVersion SSE 事件协议版本
 * @param conversationId 实际使用的会话 ID
 * @param diagnosisId 本次诊断运行 ID，可用于后续查询和问题定位
 */
public record StreamMetadata(
        String protocolVersion,
        UUID conversationId,
        UUID diagnosisId
) {
}
