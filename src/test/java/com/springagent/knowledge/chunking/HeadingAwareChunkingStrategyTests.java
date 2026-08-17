package com.springagent.knowledge.chunking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.springagent.knowledge.parser.ParsedDocument;
import com.springagent.knowledge.parser.Section;
import java.util.List;
import org.junit.jupiter.api.Test;

class HeadingAwareChunkingStrategyTests {

    @Test
    void injectsHeadingPathIntoChunkTextAndMetadata() {
        HeadingAwareChunkingStrategy strategy = strategy(1000, 0);
        ParsedDocument document = new ParsedDocument(
                "test",
                "Upgrade Notes",
                List.of(
                        new Section("Upgrade Notes", 1, "Introduction text."),
                        new Section("Jakarta", 2, "Migration details here."),
                        new Section("Config", 2, "Property renames.")
                )
        );

        List<Chunk> chunks = strategy.chunk(document);

        assertEquals(3, chunks.size());
        assertTrue(chunks.get(0).text()
                .startsWith("Upgrade Notes\n\nIntroduction text."));
        assertTrue(chunks.get(1).text()
                .startsWith("Upgrade Notes > Jakarta\n\nMigration details here."));
        assertEquals(
                "Upgrade Notes > Jakarta",
                chunks.get(1).metadata().get("heading_path")
        );
        assertEquals(
                "Upgrade Notes > Config",
                chunks.get(2).metadata().get("heading_path")
        );
    }

    @Test
    void resetsDeeperHeadingPathWhenParentChanges() {
        HeadingAwareChunkingStrategy strategy = strategy(1000, 0);
        ParsedDocument document = new ParsedDocument(
                "test",
                "Guide",
                List.of(
                        new Section("Guide", 1, "Intro."),
                        new Section("Old Topic", 2, "Details."),
                        new Section("New Topic", 1, "Details.")
                )
        );

        List<Chunk> chunks = strategy.chunk(document);

        // 新的 H1 出现后，H2 的旧路径不应残留在新路径里
        assertEquals(
                "New Topic",
                chunks.get(2).metadata().get("heading_path")
        );
    }

    @Test
    void splitsOversizedSectionKeepingHeadingOnEveryPiece() {
        HeadingAwareChunkingStrategy strategy = strategy(20, 0);
        ParsedDocument document = new ParsedDocument(
                "test",
                "Big Section",
                List.of(new Section(
                        "Big Section",
                        1,
                        "a".repeat(100)
                ))
        );

        List<Chunk> chunks = strategy.chunk(document);

        assertTrue(chunks.size() >= 2);
        for (Chunk chunk : chunks) {
            assertTrue(chunk.text().startsWith("Big Section\n\n"));
        }
    }

    private HeadingAwareChunkingStrategy strategy(int chunkSize, int overlap) {
        KnowledgeChunkingProperties properties =
                new KnowledgeChunkingProperties();
        properties.setChunkSize(chunkSize);
        properties.setOverlap(overlap);
        return new HeadingAwareChunkingStrategy(properties);
    }
}
