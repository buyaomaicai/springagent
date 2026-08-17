package com.springagent.knowledge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.springagent.knowledge.retrieval.HybridRetrievalStrategy;
import com.springagent.knowledge.retrieval.RetrievedEvidence;
import com.springagent.knowledge.retrieval.RetrievalChannel;
import com.springagent.knowledge.retrieval.RetrievalRequest;
import com.springagent.knowledge.retrieval.SearchResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;

@ExtendWith(MockitoExtension.class)
class KnowledgeRetrievalServiceTests {

    @Mock
    private HybridRetrievalStrategy hybridStrategy;

    @Test
    void delegatesSearchToHybridStrategy() {
        Document doc = Document.builder()
                .id("id").text("content").build();
        SearchResult expected = new SearchResult(
                List.of(new RetrievedEvidence(
                        doc, 0.9, RetrievalChannel.HYBRID
                )),
                true
        );
        when(hybridStrategy.retrieve(any())).thenReturn(expected);
        KnowledgeRetrievalService service =
                new KnowledgeRetrievalService(hybridStrategy);

        SearchResult result = service.search(new RetrievalRequest(
                "question", 5, 0.0, Map.of()
        ));

        assertSame(expected, result);
        verify(hybridStrategy).retrieve(any(RetrievalRequest.class));
    }

    @Test
    void searchSpringBoot30AppliesComponentFilter() {
        when(hybridStrategy.retrieve(any())).thenReturn(new SearchResult(
                List.of(),
                false
        ));
        KnowledgeRetrievalService service =
                new KnowledgeRetrievalService(hybridStrategy);

        List<Document> documents = service.searchSpringBoot30("question");

        assertEquals(0, documents.size());
        verify(hybridStrategy).retrieve(org.mockito.ArgumentMatchers.argThat(
                request -> "spring-boot".equals(
                        request.filters().get("component")
                ) && "3.0".equals(request.filters().get("target_version"))
        ));
    }
}
