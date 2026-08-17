package com.springagent.knowledge.retrieval;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

/**
 * 向量检索通道：pgvector 语义相似度 + 元数据过滤。
 */
@Component
@RequiredArgsConstructor
public class VectorRetrievalStrategy implements RetrievalStrategy {

    private final VectorStore vectorStore;

    @Override
    public String name() {
        return "VECTOR";
    }

    @Override
    public SearchResult retrieve(RetrievalRequest request) {
        org.springframework.ai.vectorstore.SearchRequest.Builder builder =
                org.springframework.ai.vectorstore.SearchRequest.builder()
                .query(request.query())
                .topK(request.topK())
                .similarityThreshold(request.minScore());
        if (!request.filters().isEmpty()) {
            builder.filterExpression(buildFilterExpression(
                    request.filters()
            ));
        }

        List<Document> documents = vectorStore.similaritySearch(
                builder.build()
        );
        List<RetrievedEvidence> evidences = documents.stream()
                .map(document -> new RetrievedEvidence(
                        document,
                        document.getScore() == null
                                ? 0.0
                                : document.getScore(),
                        RetrievalChannel.VECTOR
                ))
                .toList();
        return new SearchResult(evidences, !evidences.isEmpty());
    }

    /**
     * 把过滤条件 map 转成 Spring AI 的原生过滤表达式（如
     * "component == 'spring-boot' && target_version == '3.0'"）。
     */
    private String buildFilterExpression(Map<String, String> filters) {
        return filters.entrySet().stream()
                .map(entry -> entry.getKey()
                        + " == '"
                        + escape(entry.getValue())
                        + "'")
                .collect(Collectors.joining(" && "));
    }

    private String escape(String value) {
        return value.replace("'", "\\'");
    }
}
