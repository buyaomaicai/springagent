package com.springagent.knowledge.retrieval;

import org.springframework.ai.document.Document;

/**
 * 一条检索结果：文档 + 分数 + 来源通道。
 */
public record RetrievedEvidence(
        Document document,
        double score,
        RetrievalChannel channel
) {
}
