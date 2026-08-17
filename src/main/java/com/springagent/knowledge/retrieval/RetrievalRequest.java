package com.springagent.knowledge.retrieval;

import java.util.Map;

/**
 * 一次检索请求：查询文本、返回条数、最低分数与元数据过滤条件。
 *
 * <p>minScore 的语义随通道而异（余弦相似度 / 三元组相似度），
 * 阈值标定属于评估阶段（Recall@K 调参）的工作。</p>
 */
public record RetrievalRequest(
        String query,
        int topK,
        double minScore,
        Map<String, String> filters
) {
    public RetrievalRequest {
        filters = Map.copyOf(filters);
    }
}
