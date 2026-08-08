package com.springagent.knowledge.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KnowledgeRetrievalService {

    private final VectorStore vectorStore;

    public List<Document> searchSpringBoot30(String question) {
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(5)
                        .similarityThreshold(0.55)
                        .filterExpression("""
                                  component == 'spring-boot' &&
                                  target_version == '3.0'
                                  """)
                        .build()
        );
    }
}