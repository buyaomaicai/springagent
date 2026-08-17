package com.springagent.knowledge.chunking;

import com.springagent.knowledge.parser.ParsedDocument;
import com.springagent.knowledge.parser.Section;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 固定大小策略：把整篇文档按固定窗口 + 滑动窗口 overlap 切分（基准策略）。
 */
@Component
@RequiredArgsConstructor
public class FixedSizeChunkingStrategy implements ChunkingStrategy {

    private final KnowledgeChunkingProperties properties;

    @Override
    public String name() {
        return "FIXED";
    }

    @Override
    public List<Chunk> chunk(ParsedDocument document) {
        String text = document.sections().stream()
                .map(Section::text)
                .collect(Collectors.joining("\n\n"));
        return FixedWindow.split(
                text,
                properties.getChunkSize(),
                properties.getOverlap()
        );
    }
}
