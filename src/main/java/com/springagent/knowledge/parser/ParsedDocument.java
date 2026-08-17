package com.springagent.knowledge.parser;

import java.util.List;

/**
 * 一份已解析的知识文档：带章节结构（供 P1 标题感知分块）。
 *
 * <p>解析器只负责"识别结构"（标题/正文/表格），不负责元数据——
 * 入库元数据（source_id/component/language 等）由入库服务在同时知道
 * 来源定义与文档定义的地方统一组装，避免元数据散落两处。</p>
 */
public record ParsedDocument(
        String sourceId,
        String title,
        List<Section> sections
) {
    public ParsedDocument {
        sections = List.copyOf(sections);
    }
}
