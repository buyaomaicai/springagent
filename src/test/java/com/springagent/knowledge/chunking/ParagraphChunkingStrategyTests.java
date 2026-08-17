package com.springagent.knowledge.chunking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.springagent.knowledge.parser.ParsedDocument;
import com.springagent.knowledge.parser.Section;
import java.util.List;
import org.junit.jupiter.api.Test;

class ParagraphChunkingStrategyTests {

    @Test
    void groupsParagraphsUntilTargetSize() {
        ParagraphChunkingStrategy strategy = strategy(20, 0);
        ParsedDocument document = document(
                "aaaa" + "\n\n" + "bbbb" + "\n\n" + "cccc"
                        + "\n\n" + "dddd"
        );

        List<Chunk> chunks = strategy.chunk(document);

        // 4 段各 4 字符（每段含分隔符共 6 字符）：20 字符容纳 3 段，第 4 段触发切分
        assertEquals(2, chunks.size());
        assertTrue(chunks.get(0).text().contains("aaaa"));
        assertTrue(chunks.get(0).text().contains("bbbb"));
        assertTrue(chunks.get(0).text().contains("cccc"));
        assertFalse(chunks.get(0).text().contains("dddd"));
        assertTrue(chunks.get(1).text().contains("dddd"));
    }

    @Test
    void splitsOversizedParagraphInternally() {
        ParagraphChunkingStrategy strategy = strategy(10, 0);
        ParsedDocument document = document("a".repeat(25));

        List<Chunk> chunks = strategy.chunk(document);

        assertTrue(chunks.size() >= 2);
        for (Chunk chunk : chunks) {
            assertTrue(chunk.text().length() <= 10);
        }
    }

    @Test
    void carriesParagraphTailAsOverlap() {
        ParagraphChunkingStrategy strategy = strategy(30, 5);
        // 段 1 20 字符 + 段 2 20 字符 > 30 → 切两个 chunk；段 1 尾部 5 字符带入段 2 开头
        String p1 = "a".repeat(20);
        String p2 = "b".repeat(20);
        ParsedDocument document = document(p1 + "\n\n" + p2);

        List<Chunk> chunks = strategy.chunk(document);

        assertEquals(2, chunks.size());
        assertTrue(chunks.get(1).text().startsWith("aaaaa"));
        assertTrue(chunks.get(1).text().contains("bbbbbbbbbbbbbbbbbbbb"));
    }

    private ParagraphChunkingStrategy strategy(int chunkSize, int overlap) {
        KnowledgeChunkingProperties properties =
                new KnowledgeChunkingProperties();
        properties.setChunkSize(chunkSize);
        properties.setOverlap(overlap);
        return new ParagraphChunkingStrategy(properties);
    }

    private ParsedDocument document(String text) {
        return new ParsedDocument(
                "test",
                "Test",
                List.of(new Section("Test", 1, text))
        );
    }
}
