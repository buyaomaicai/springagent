package com.springagent.knowledge.retrieval;

/**
 * 检索结果的来源通道，用于可观测性（评估时分析哪条通道贡献了命中）。
 */
public enum RetrievalChannel {
    VECTOR,
    KEYWORD,
    HYBRID
}
