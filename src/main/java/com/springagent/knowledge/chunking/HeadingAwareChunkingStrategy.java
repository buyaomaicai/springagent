package com.springagent.knowledge.chunking;

import com.springagent.knowledge.parser.ParsedDocument;
import com.springagent.knowledge.parser.Section;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 标题感知策略：每个章节一个 chunk，正文前注入"章节路径"（标题注入），
 * 超过窗口的章节内部再用固定窗口（带 overlap）切分，每一片都带标题前缀。
 *
 * <p>标题注入让 chunk 自带"我在讲哪个主题"的上下文，检索"配置变化"时
 * 能命中属于配置章节的 chunk；章节路径写入 metadata（heading_path），
 * 可追溯 chunk 在文档中的位置。</p>
 */
@Component
@RequiredArgsConstructor
public class HeadingAwareChunkingStrategy implements ChunkingStrategy {

    private static final int MAX_HEADING_LEVEL = 6;

    private final KnowledgeChunkingProperties properties;

    @Override
    public String name() {
        return "HEADING_AWARE";
    }

    @Override
    public List<Chunk> chunk(ParsedDocument document) {
        List<Chunk> chunks = new ArrayList<>();
        String[] lastTitleByLevel = new String[MAX_HEADING_LEVEL + 2];

        for (Section section : document.sections()) {
            lastTitleByLevel[section.level()] = section.title();
            // 当前层级以下的旧标题不再属于新章节的路径
            for (int level = section.level() + 1;
                    level < lastTitleByLevel.length;
                    level++) {
                lastTitleByLevel[level] = null;
            }

            String headingPath = buildPath(lastTitleByLevel, section.level());
            Map<String, String> metadata = Map.of(
                    "heading_path", headingPath
            );
            String content = section.text();

            if (content.length() <= properties.getChunkSize()) {
                chunks.add(new Chunk(
                        headingPath + "\n\n" + content,
                        metadata
                ));
            } else {
                // 超长章节按内容切分，每一片都重新注入标题前缀（不依赖第一片）
                for (Chunk piece : FixedWindow.split(
                        content,
                        properties.getChunkSize(),
                        properties.getOverlap()
                )) {
                    chunks.add(new Chunk(
                            headingPath + "\n\n" + piece.text(),
                            metadata
                    ));
                }
            }
        }
        return chunks;
    }

    private String buildPath(String[] lastTitleByLevel, int level) {
        StringBuilder path = new StringBuilder();
        for (int current = 1; current <= level; current++) {
            String title = lastTitleByLevel[current];
            if (title == null) {
                continue;
            }
            if (!path.isEmpty()) {
                path.append(" > ");
            }
            path.append(title);
        }
        return path.isEmpty() ? "Untitled" : path.toString();
    }
}
