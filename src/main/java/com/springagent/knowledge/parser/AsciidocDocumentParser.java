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
 * Asciidoc 解析器：按标题层级（= / == / ===）切分章节，表格等正文原样保留。
 */
@Component
public class AsciidocDocumentParser implements DocumentParser {

    @Override
    public KnowledgeFileFormat supportedFormat() {
        return KnowledgeFileFormat.ASCIIDOC;
    }

    @Override
    public ParsedDocument parse(Path file, SourceDocument definition) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Asciidoc 文档读取失败: " + file,
                    exception
            );
        }

        List<Section> sections = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        String currentTitle = null;
        int currentLevel = 1;
        String documentTitle = null;

        for (String rawLine : lines) {
            Heading heading = headingOf(rawLine);
            if (heading != null) {
                flushSection(sections, buffer, currentTitle, currentLevel);
                currentTitle = heading.title();
                currentLevel = heading.level();
                if (documentTitle == null && heading.level() == 1) {
                    documentTitle = heading.title();
                }
            } else {
                if (!buffer.isEmpty()) {
                    buffer.append('\n');
                }
                buffer.append(rawLine);
            }
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
     * 识别 asciidoc 标题：行首连续 '=' 后跟空格和文字；
     * 只有 '=' 没有文字（如 "===="）不是标题。
     */
    private Heading headingOf(String line) {
        String stripped = line.strip();
        if (stripped.isEmpty() || stripped.charAt(0) != '=') {
            return null;
        }
        int level = 0;
        while (level < stripped.length()
                && stripped.charAt(level) == '=') {
            level++;
        }
        if (level >= stripped.length()
                || stripped.charAt(level) != ' ') {
            return null;
        }
        String title = stripped.substring(level).strip();
        if (title.isEmpty()) {
            return null;
        }
        return new Heading(level, title);
    }

    private record Heading(int level, String title) {
    }
}
