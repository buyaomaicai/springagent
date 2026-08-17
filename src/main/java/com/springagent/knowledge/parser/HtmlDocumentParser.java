package com.springagent.knowledge.parser;

import com.springagent.knowledge.source.SourceDocument;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Component;

/**
 * HTML 解析器：用 jsoup 解析，去除导航/页脚噪音后按标题层级切分章节，
 * 表格转换为 Markdown 表格（保留结构，避免表格退化成一行文字）。
 */
@Component
public class HtmlDocumentParser implements DocumentParser {

    private static final String NOISE_SELECTOR =
            "nav, footer, aside, script, style, form, "
                    + ".nav, .footer, .breadcrumb, .toc, #toc";

    @Override
    public KnowledgeFileFormat supportedFormat() {
        return KnowledgeFileFormat.HTML;
    }

    @Override
    public ParsedDocument parse(Path file, SourceDocument definition) {
        Document document = parseDocument(file);
        Element content = selectContent(document);
        content.select(NOISE_SELECTOR).remove();

        State state = new State();
        walk(content, state);
        state.flush();

        String title = state.title == null
                ? definition.fileName()
                : state.title;
        return new ParsedDocument(definition.fileName(), title, state.sections);
    }

    private Document parseDocument(Path file) {
        try {
            return Jsoup.parse(
                    new File(file.toString()),
                    StandardCharsets.UTF_8.name()
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "HTML 文档读取失败: " + file,
                    exception
            );
        }
    }

    private Element selectContent(Document document) {
        Element main = document.selectFirst("main, article");
        if (main != null) {
            return main;
        }
        Element body = document.body();
        return body == null ? document : body;
    }

    /**
     * 深度优先遍历正文节点：标题开启新章节，表格/代码块/文本追加到当前章节。
     */
    private void walk(Node node, State state) {
        if (node instanceof TextNode textNode) {
            state.buffer.append(textNode.text());
            return;
        }
        if (!(node instanceof Element element)) {
            return;
        }
        String tag = element.tagName().toLowerCase();
        if (isHeading(tag)) {
            int level = Integer.parseInt(tag.substring(1));
            String heading = element.text().strip();
            state.flush();
            state.currentTitle = heading;
            state.currentLevel = level;
            if (state.title == null && level == 1) {
                state.title = heading;
            }
            return;
        }
        switch (tag) {
            case "table" -> {
                state.buffer.append('\n')
                        .append(tableToMarkdown(element))
                        .append('\n');
                return;
            }
            case "pre" -> {
                state.buffer.append("\n```\n")
                        .append(element.text())
                        .append("\n```\n");
                return;
            }
            case "br" -> {
                state.buffer.append('\n');
                return;
            }
            case "a" -> {
                // 只保留链接文字，避免把 URL 噪音带进向量
                state.buffer.append(element.text());
                return;
            }
            case "img" -> {
                state.buffer.append(element.attr("alt"));
                return;
            }
            default -> {
                // 其余块级元素：前后换行 + 递归子节点
                boolean block = isBlockLevel(tag);
                if (block) {
                    state.buffer.append('\n');
                }
                for (Node child : element.childNodes()) {
                    walk(child, state);
                }
                if (block) {
                    state.buffer.append('\n');
                }
            }
        }
    }

    private boolean isHeading(String tag) {
        return tag.length() == 2
                && tag.charAt(0) == 'h'
                && tag.charAt(1) >= '1'
                && tag.charAt(1) <= '6';
    }

    private boolean isBlockLevel(String tag) {
        return switch (tag) {
            case "div", "p", "li", "ul", "ol", "section", "article",
                 "main", "body", "blockquote", "dl", "dd", "dt",
                 "figure", "h1", "h2", "h3", "h4", "h5", "h6",
                 "tr", "thead", "tbody" -> true;
            default -> false;
        };
    }

    /**
     * HTML 表格 → Markdown 表格（| 列 | 列 |），列数按首行对齐，空单元格保留占位。
     */
    private String tableToMarkdown(Element table) {
        List<List<String>> rows = new ArrayList<>();
        for (Element tr : table.select("tr")) {
            List<String> cells = new ArrayList<>();
            for (Element cell : tr.select("th, td")) {
                cells.add(cell.text().strip());
            }
            rows.add(cells);
        }
        if (rows.isEmpty()) {
            return "";
        }
        int columns = rows.stream()
                .mapToInt(List::size)
                .max()
                .orElse(1);

        StringBuilder markdown = new StringBuilder();
        appendRow(markdown, rows.get(0), columns);
        markdown.append('|');
        for (int i = 0; i < columns; i++) {
            markdown.append(" --- |");
        }
        markdown.append('\n');
        for (int i = 1; i < rows.size(); i++) {
            appendRow(markdown, rows.get(i), columns);
        }
        return markdown.toString();
    }

    private void appendRow(
            StringBuilder markdown,
            List<String> cells,
            int columns
    ) {
        markdown.append('|');
        for (int i = 0; i < columns; i++) {
            String cell = i < cells.size() ? cells.get(i) : "";
            markdown.append(' ')
                    .append(cell.replace("|", "\\|"))
                    .append(" |");
        }
        markdown.append('\n');
    }

    /**
     * 遍历过程的可变状态：已收集章节、当前章节缓冲、当前标题/层级、文档标题。
     */
    private static final class State {
        final List<Section> sections = new ArrayList<>();
        final StringBuilder buffer = new StringBuilder();
        String currentTitle;
        int currentLevel = 1;
        String title;

        void flush() {
            String text = buffer.toString().strip();
            if (!text.isEmpty()) {
                sections.add(new Section(
                        currentTitle == null ? "Untitled" : currentTitle,
                        currentLevel,
                        text
                ));
                buffer.setLength(0);
            }
        }
    }
}
