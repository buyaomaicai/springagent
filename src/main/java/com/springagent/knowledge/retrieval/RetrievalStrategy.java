package com.springagent.knowledge.retrieval;

/**
 * 检索策略：把查询变成带分数的证据列表。
 *
 * <p>策略接口让"向量/关键词/混合"可插拔、可对比（P5 评估），
 * 调用方只依赖接口。</p>
 */
public interface RetrievalStrategy {

    String name();

    SearchResult retrieve(RetrievalRequest request);
}
