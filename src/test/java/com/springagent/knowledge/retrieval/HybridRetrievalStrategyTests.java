package com.springagent.knowledge.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;

/**
 * 验证 RRF 融合公式：score(d) = Σ 1/(k + rank)，k=60。
 * 两通道排名可手算，断言融合分与排序。
 */
@ExtendWith(MockitoExtension.class)
class HybridRetrievalStrategyTests {

    @Mock
    private VectorRetrievalStrategy vectorStrategy;

    @Mock
    private KeywordRetrievalStrategy keywordStrategy;

    @Test
    void fusesRanksWithRrfFormula() {
        Document docA = Document.builder()
                .id("id-a").text("A content").build();
        Document docB = Document.builder()
                .id("id-b").text("B content").build();
        // 向量通道：A 第 1、B 第 2
        when(vectorStrategy.retrieve(any())).thenReturn(new SearchResult(
                List.of(
                        new RetrievedEvidence(
                                docA, 0.9, RetrievalChannel.VECTOR
                        ),
                        new RetrievedEvidence(
                                docB, 0.8, RetrievalChannel.VECTOR
                        )
                ),
                true
        ));
        // 关键词通道：B 第 1、C 第 2、A 第 3
        when(keywordStrategy.retrieve(any())).thenReturn(new SearchResult(
                List.of(
                        new RetrievedEvidence(
                                docB, 0.7, RetrievalChannel.KEYWORD
                        ),
                        new RetrievedEvidence(
                                Document.builder()
                                        .id("id-c").text("C content")
                                        .build(),
                                0.6,
                                RetrievalChannel.KEYWORD
                        ),
                        new RetrievedEvidence(
                                docA, 0.5, RetrievalChannel.KEYWORD
                        )
                ),
                true
        ));
        HybridRetrievalStrategy hybrid = new HybridRetrievalStrategy(
                vectorStrategy,
                keywordStrategy
        );

        SearchResult result = hybrid.retrieve(new RetrievalRequest(
                "query", 2, 0.0, Map.of()
        ));

        // A = 1/61 + 1/63；B = 1/62 + 1/61 → B 排第一
        assertEquals(2, result.evidences().size());
        assertEquals("id-b", result.evidences().get(0).document().getId());
        assertEquals("id-a", result.evidences().get(1).document().getId());
        assertEquals(
                1.0 / 62 + 1.0 / 61,
                result.evidences().get(0).score(),
                1e-9
        );
        assertEquals(
                1.0 / 61 + 1.0 / 63,
                result.evidences().get(1).score(),
                1e-9
        );
        assertEquals(
                RetrievalChannel.HYBRID,
                result.evidences().get(0).channel()
        );
    }

    @Test
    void reportsNotFoundWhenBothChannelsReturnNothing() {
        when(vectorStrategy.retrieve(any()))
                .thenReturn(new SearchResult(List.of(), false));
        when(keywordStrategy.retrieve(any()))
                .thenReturn(new SearchResult(List.of(), false));
        HybridRetrievalStrategy hybrid = new HybridRetrievalStrategy(
                vectorStrategy,
                keywordStrategy
        );

        SearchResult result = hybrid.retrieve(new RetrievalRequest(
                "nothing", 5, 0.0, Map.of()
        ));

        assertFalse(result.found());
        assertTrue(result.evidences().isEmpty());
    }
}
