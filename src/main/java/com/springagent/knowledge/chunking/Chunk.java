package com.springagent.knowledge.chunking;

import java.util.Map;

/**
 * 一个分块结果：文本与结构元数据（如标题路径）。
 *
 * <p>序号不入模型：列表位置即序号，由入库服务统一分配（保证跨策略唯一）。
 * 入库服务负责组装来源级元数据（source_id/component 等），
 * 分块器只负责切分和补充结构元数据（如 heading_path）。</p>
 */
public record Chunk(
        String text,
        Map<String, String> metadata
) {
    public Chunk {
        metadata = Map.copyOf(metadata);
    }
}
