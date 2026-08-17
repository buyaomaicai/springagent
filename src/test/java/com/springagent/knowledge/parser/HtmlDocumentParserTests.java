package com.springagent.knowledge.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.springagent.knowledge.source.SourceDocument;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HtmlDocumentParserTests {

    @TempDir
    Path tempDir;

    private final HtmlDocumentParser parser = new HtmlDocumentParser();

    @Test
    void convertsTableToMarkdownTable() throws IOException {
        Path file = write("guide.html", """
                <html><body>
                <h1>Migration Guide</h1>
                <h2>Versions</h2>
                <table>
                  <tr><th>Old</th><th>New</th></tr>
                  <tr><td>2.7</td><td>3.0</td></tr>
                </table>
                </body></html>
                """);

        ParsedDocument document = parser.parse(
                file,
                new SourceDocument(
                        "guide.html",
                        "MIGRATION_GUIDE",
                        "3.0",
                        "https://example.com/guide"
                )
        );

        assertEquals("Migration Guide", document.title());
        // <h1> 章节正文为空不产生 Section，表格挂在 <h2>Versions</h2> 章节下
        assertEquals(1, document.sections().size());
        String text = document.sections().get(0).text();
        assertTrue(text.contains("| Old | New |"), text);
        assertTrue(text.contains("| --- | --- |"), text);
        assertTrue(text.contains("| 2.7 | 3.0 |"), text);
    }

    @Test
    void removesNavigationAndFooterNoise() throws IOException {
        Path file = write("guide.html", """
                <html><body>
                <nav>Sidebar menu link</nav>
                <h1>Real Guide</h1>
                <p>Real content here.</p>
                <footer>Copyright footer</footer>
                </body></html>
                """);

        ParsedDocument document = parser.parse(
                file,
                new SourceDocument(
                        "guide.html",
                        "MIGRATION_GUIDE",
                        "3.0",
                        "https://example.com/guide"
                )
        );

        String allText = document.sections().stream()
                .map(Section::text)
                .reduce("", (a, b) -> a + "\n" + b);
        assertTrue(allText.contains("Real content here."));
        assertFalse(allText.contains("Sidebar menu link"));
        assertFalse(allText.contains("Copyright footer"));
    }

    @Test
    void usesFirstHeadingAsTitleAndKeepsLinkTextOnly() throws IOException {
        Path file = write("guide.html", """
                <html><body>
                <h1>Jakarta Migration</h1>
                <p>See <a href="https://example.com/target">the migration notes</a>.</p>
                </body></html>
                """);

        ParsedDocument document = parser.parse(
                file,
                new SourceDocument(
                        "guide.html",
                        "MIGRATION_GUIDE",
                        "3.0",
                        "https://example.com/guide"
                )
        );

        assertEquals("Jakarta Migration", document.title());
        String text = document.sections().get(0).text();
        assertTrue(text.contains("the migration notes"));
        assertFalse(text.contains("https://example.com/target"));
    }

    private Path write(String name, String content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}
