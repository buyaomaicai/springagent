package com.springagent.knowledge.chunking;

import com.springagent.knowledge.parser.ParsedDocument;
import com.springagent.knowledge.parser.Section;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 段落聚合策略：按空行分段落，把段落聚合到目标大小再切。
 *
 * <p>相比固定窗口，chunk 边界落在段落之间，语义更完整；
 * 超出窗口的大段落内部再用固定窗口（带 overlap）切分。
 * overlap 表现为"上一个 chunk 的最后一段尾部带入下一个 chunk 开头"。</p>
 */
@Component
@RequiredArgsConstructor
public class ParagraphChunkingStrategy implements ChunkingStrategy {

    private static final Pattern PARAGRAPH_SPLIT = Pattern.compile(
            "\\n\\s*\\n"
    );

    private final KnowledgeChunkingProperties properties;

    @Override
    public String name() {
        return "PARAGRAPH";
    }

    @Override
    public List<Chunk> chunk(ParsedDocument document) {
        String text = document.sections().stream()
                .map(Section::text)
                .collect(Collectors.joining("\n\n"));

        String[] paragraphs = PARAGRAPH_SPLIT.split(text);
        List<Chunk> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String paragraph : paragraphs) {
            String para = paragraph.strip();
            if (para.isEmpty()) {
                continue;
            }
            if (para.length() > properties.getChunkSize()) {
                flush(chunks, current);
                chunks.addAll(FixedWindow.split(
                        para,
                        properties.getChunkSize(),
                        properties.getOverlap()
                ));
                continue;
            }
            if (current.length() > 0
                    && current.length() + para.length()
                    > properties.getChunkSize()) {
                String carried = overlapTail(current);
                flush(chunks, current);
                if (!carried.isEmpty()) {
                    current.append(carried).append("\n\n");
                }
            }
            current.append(para).append("\n\n");
        }
        flush(chunks, current);
        return chunks;
    }

    private void flush(List<Chunk> chunks, StringBuilder current) {
        String text = current.toString().strip();
        if (!text.isEmpty()) {
            chunks.add(new Chunk(text, Map.of()));
        }
    }

    /**
     * 取当前缓冲最后一段的尾部（最多 overlap 个字符）作为下一个 chunk 的开头，
     * 保证段落边界的上下文不断裂。
     */
    private String overlapTail(StringBuilder current) {
        String text = current.toString().strip();
        String[] paragraphs = PARAGRAPH_SPLIT.split(text);
        if (paragraphs.length == 0) {
            return "";
        }
        String last = paragraphs[paragraphs.length - 1].strip();
        int overlap = properties.getOverlap();
        return last.length() <= overlap
                ? last
                : last.substring(last.length() - overlap);
    }
}
