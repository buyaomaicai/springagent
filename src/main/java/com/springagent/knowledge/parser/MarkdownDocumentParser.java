package com.springagent.knowledge.parser;

import com.springagent.knowledge.source.SourceDocument;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Markdown 解析器：按标题层级（# / ## / ###）切分章节，
 * 代码围栏（``` / ~~~）内的内容不当作标题处理。
 */
@Component
public class MarkdownDocumentParser implements DocumentParser {

    @Override
    public KnowledgeFileFormat supportedFormat() {
        return KnowledgeFileFormat.MARKDOWN;
    }

    @Override
    public ParsedDocument parse(Path file, SourceDocument definition) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Markdown 文档读取失败: " + file,
                    exception
            );
        }

        List<Section> sections = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        String currentTitle = null;
        int currentLevel = 1;
        String documentTitle = null;
        boolean inFence = false;

        for (String rawLine : lines) {
            String stripped = rawLine.strip();
            if (stripped.startsWith("```") || stripped.startsWith("~~~")) {
                inFence = !inFence;
                if (!buffer.isEmpty()) {
                    buffer.append('\n');
                }
                buffer.append(rawLine);
                continue;
            }
            if (!inFence) {
                Heading heading = headingOf(stripped);
                if (heading != null) {
                    flushSection(sections, buffer, currentTitle, currentLevel);
                    currentTitle = heading.title();
                    currentLevel = heading.level();
                    if (documentTitle == null && heading.level() == 1) {
                        documentTitle = heading.title();
                    }
                    continue;
                }
            }
            if (!buffer.isEmpty()) {
                buffer.append('\n');
            }
            buffer.append(rawLine);
        }
        flushSection(sections, buffer, currentTitle, currentLevel);

        String title = documentTitle == null
                ? definition.fileName()
                : documentTitle;
        return new ParsedDocument(definition.fileName(), title, sections);
    }

    private void flushSection(
            List<Section> sections,
            StringBuilder buffer,
            String currentTitle,
            int currentLevel
    ) {
        String text = buffer.toString().strip();
        if (text.isEmpty()) {
            return;
        }
        sections.add(new Section(
                currentTitle == null ? "Untitled" : currentTitle,
                currentLevel,
                text
        ));
        buffer.setLength(0);
    }

    /**
     * 识别 markdown 标题：行首 1-6 个 '#' 后跟空格和文字。
     */
    private Heading headingOf(String line) {
        if (line.isEmpty() || line.charAt(0) != '#') {
            return null;
        }
        int level = 0;
        while (level < line.length() && line.charAt(level) == '#') {
            level++;
        }
        if (level > 6 || level >= line.length()
                || line.charAt(level) != ' ') {
            return null;
        }
        String title = line.substring(level).strip();
        if (title.isEmpty()) {
            return null;
        }
        return new Heading(level, title);
    }

    private record Heading(int level, String title) {
    }
}
