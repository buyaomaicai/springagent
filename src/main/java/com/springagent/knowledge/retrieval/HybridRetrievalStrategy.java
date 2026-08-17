package com.springagent.knowledge.retrieval;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 混合检索：向量 + 关键词双通道，RRF（倒数排序融合）合并。
 *
 * <p>两通道分数分布不同（余弦 0~1 vs 三元组相似度），不能直接相加；
 * RRF 只看排名：score(d) = Σ 1/(k + rank_i(d))，k 常取 60，天然鲁棒。</p>
 */
@Component
@RequiredArgsConstructor
public class HybridRetrievalStrategy implements RetrievalStrategy {

    private static final int RRF_K = 60;
    private static final int POOL_MULTIPLIER = 3;

    private final VectorRetrievalStrategy vectorStrategy;
    private final KeywordRetrievalStrategy keywordStrategy;

    @Override
    public String name() {
        return "HYBRID";
    }

    @Override
    public SearchResult retrieve(RetrievalRequest request) {
        // 每通道召回更大的候选池，融合后取 topK
        int poolSize = Math.max(1, request.topK() * POOL_MULTIPLIER);
        RetrievalRequest poolRequest = new RetrievalRequest(
                request.query(),
                poolSize,
                request.minScore(),
                request.filters()
        );

        Map<String, RetrievedEvidence> fused = new LinkedHashMap<>();
        fuse(fused, vectorStrategy.retrieve(poolRequest));
        fuse(fused, keywordStrategy.retrieve(poolRequest));

        List<RetrievedEvidence> ranked = fused.values().stream()
                .sorted((left, right) -> Double.compare(
                        right.score(),
                        left.score()
                ))
                .limit(request.topK())
                .toList();
        return new SearchResult(ranked, !ranked.isEmpty());
    }

    private void fuse(
            Map<String, RetrievedEvidence> fused,
            SearchResult channelResult
    ) {
        List<RetrievedEvidence> evidences = channelResult.evidences();
        for (int index = 0; index < evidences.size(); index++) {
            RetrievedEvidence evidence = evidences.get(index);
            String documentId = evidence.document().getId();
            double rrfScore = 1.0 / (RRF_K + index + 1);
            fused.merge(
                    documentId,
                    new RetrievedEvidence(
                            evidence.document(),
                            rrfScore,
                            RetrievalChannel.HYBRID
                    ),
                    (existing, added) -> new RetrievedEvidence(
                            existing.document(),
                            existing.score() + rrfScore,
                            RetrievalChannel.HYBRID
                    )
            );
        }
    }
}
