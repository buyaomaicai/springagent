package com.springagent.knowledge.service;

import com.springagent.knowledge.retrieval.HybridRetrievalStrategy;
import com.springagent.knowledge.retrieval.RetrievedEvidence;
import com.springagent.knowledge.retrieval.RetrievalRequest;
import com.springagent.knowledge.retrieval.SearchResult;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

/**
 * 知识检索服务：对外提供混合检索（向量 + 关键词 + RRF）。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeRetrievalService {

    private final HybridRetrievalStrategy hybridStrategy;

    public SearchResult search(RetrievalRequest request) {
        return hybridStrategy.retrieve(request);
    }

    /**
     * 兼容旧调用（RAG 冒烟测试）：限定 spring-boot 3.0 文档。
     */
    public List<Document> searchSpringBoot30(String question) {
        SearchResult result = hybridStrategy.retrieve(new RetrievalRequest(
                question,
                5,
                0.0,
                Map.of(
                        "component", "spring-boot",
                        "target_version", "3.0"
                )
        ));
        return result.evidences().stream()
                .map(RetrievedEvidence::document)
                .toList();
    }
}
