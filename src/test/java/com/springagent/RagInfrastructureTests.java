package com.springagent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class RagInfrastructureTests {

    @Autowired
    private VectorStore vectorStore;

    @Test
    void retrievesEnglishDocumentWithChineseQuery() {
        String id = UUID.randomUUID().toString();

        Document document = Document.builder()
                .id(id)
                .text("""
                        Spring Boot 3.0 requires Java 17.
                        Applications must migrate from javax packages
                        to the Jakarta EE jakarta namespace.
                        """)
                .metadata("source_id", "smoke-test")
                .metadata("component", "spring-boot")
                .metadata("target_version", "3.0")
                .build();

        boolean added = false;
        try {
            vectorStore.add(List.of(document));
            added = true;

            List<Document> results = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query("Spring Boot 3 最低需要哪个 Java 版本？")
                            .topK(3)
                            .similarityThreshold(0.5)
                            .build()
            );

            assertFalse(results.isEmpty());
            assertTrue(results.stream()
                    .anyMatch(result ->
                            result.getText().contains("Java 17")));
        } finally {
            if (added) {
                vectorStore.delete(List.of(id));
            }
        }
    }
}
