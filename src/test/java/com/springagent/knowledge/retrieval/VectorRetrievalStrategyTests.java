package com.springagent.knowledge.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

@ExtendWith(MockitoExtension.class)
class VectorRetrievalStrategyTests {

    @Mock
    private VectorStore vectorStore;

    @Test
    void buildsSearchRequestWithMetadataFilters() {
        Document scored = Document.builder()
                .id("chunk-1")
                .text("migration content")
                .score(0.85)
                .build();
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(scored));
        VectorRetrievalStrategy strategy =
                new VectorRetrievalStrategy(vectorStore);

        SearchResult result = strategy.retrieve(new RetrievalRequest(
                "how to migrate",
                5,
                0.5,
                Map.of("component", "spring-boot")
        ));

        ArgumentCaptor<SearchRequest> captor =
                ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        SearchRequest sent = captor.getValue();
        assertEquals("how to migrate", sent.getQuery());
        assertEquals(5, sent.getTopK());
        assertEquals(0.5, sent.getSimilarityThreshold());
        assertTrue(sent.hasFilterExpression());

        assertEquals(1, result.evidences().size());
        assertEquals(0.85, result.evidences().get(0).score(), 1e-9);
        assertEquals(
                RetrievalChannel.VECTOR,
                result.evidences().get(0).channel()
        );
    }

    @Test
    void omitsFilterWhenNoneProvided() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of());
        VectorRetrievalStrategy strategy =
                new VectorRetrievalStrategy(vectorStore);

        strategy.retrieve(new RetrievalRequest("query", 5, 0.0, Map.of()));

        ArgumentCaptor<SearchRequest> captor =
                ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertFalse(captor.getValue().hasFilterExpression());
    }
}
