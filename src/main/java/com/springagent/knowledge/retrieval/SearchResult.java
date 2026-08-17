package com.springagent.knowledge.retrieval;

import java.util.List;

/**
 * 检索结果集：命中的证据列表与"是否找到"标记。
 *
 * <p>found=false 表示检索置信度不足，调用方应触发降级策略
 * （prompt 明示未找到 / Query Rewrite 二次检索），而不是硬凑答案。</p>
 */
public record SearchResult(
        List<RetrievedEvidence> evidences,
        boolean found
) {
    public SearchResult {
        evidences = List.copyOf(evidences);
    }
}
