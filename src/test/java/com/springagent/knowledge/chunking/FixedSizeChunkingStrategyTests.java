package com.springagent.knowledge.chunking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.springagent.knowledge.parser.ParsedDocument;
import com.springagent.knowledge.parser.Section;
import java.util.List;
import org.junit.jupiter.api.Test;

class FixedSizeChunkingStrategyTests {

    @Test
    void splitsTextIntoFixedSizeWindows() {
        FixedSizeChunkingStrategy strategy = strategy(10, 0);
        ParsedDocument document = document("abcdefghijklmnopqrst");

        List<Chunk> chunks = strategy.chunk(document);

        assertEquals(2, chunks.size());
        assertEquals("abcdefghij", chunks.get(0).text());
        assertEquals("klmnopqrst", chunks.get(1).text());
    }

    @Test
    void overlapKeepsBoundarySpanningEntityIntact() {
        // 关键实体 "javax.servlet" 起始于下标 19，恰好跨在 chunkSize=20 的边界上
        String text = "x".repeat(19) + "javax.servlet" + "y".repeat(30);

        List<Chunk> withoutOverlap = strategy(20, 0)
                .chunk(document(text));
        assertFalse(withoutOverlap.stream()
                .anyMatch(chunk -> chunk.text().contains("javax.servlet")));

        List<Chunk> withOverlap = strategy(20, 8)
                .chunk(document(text));
        assertTrue(withOverlap.stream()
                .anyMatch(chunk -> chunk.text().contains("javax.servlet")));
    }

    @Test
    void clampsOverlapToBeSmallerThanChunkSize() {
        // overlap >= chunkSize 会死循环，必须钳制（窗口每次只前进 1 字符）
        FixedSizeChunkingStrategy strategy = strategy(5, 100);
        ParsedDocument document = document("abcdefghij");

        List<Chunk> chunks = strategy.chunk(document);

        assertTrue(chunks.size() > 1);
        for (Chunk chunk : chunks) {
            assertTrue(chunk.text().length() <= 5);
        }
    }

    private FixedSizeChunkingStrategy strategy(int chunkSize, int overlap) {
        KnowledgeChunkingProperties properties =
                new KnowledgeChunkingProperties();
        properties.setChunkSize(chunkSize);
        properties.setOverlap(overlap);
        return new FixedSizeChunkingStrategy(properties);
    }

    private ParsedDocument document(String text) {
        return new ParsedDocument(
                "test",
                "Test",
                List.of(new Section("Test", 1, text))
        );
    }
}
