package com.springagent.knowledge.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.springagent.knowledge.source.SourceDocument;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MarkdownDocumentParserTests {

    @TempDir
    Path tempDir;

    private final MarkdownDocumentParser parser = new MarkdownDocumentParser();

    @Test
    void extractsHeadingHierarchy() throws IOException {
        Path file = write("notes.md", """
                # Upgrade Notes

                Introduction.

                ## Step one

                Details of step one.

                ### Sub step

                Sub details.
                """);

        ParsedDocument document = parser.parse(
                file,
                new SourceDocument(
                        "notes.md",
                        "RELEASE_NOTES",
                        "3.0",
                        "https://example.com/notes"
                )
        );

        assertEquals("Upgrade Notes", document.title());
        assertEquals(3, document.sections().size());
        assertEquals("Step one", document.sections().get(1).title());
        assertEquals(2, document.sections().get(1).level());
        assertEquals("Sub step", document.sections().get(2).title());
        assertEquals(3, document.sections().get(2).level());
    }

    @Test
    void ignoresHashLinesInsideCodeFences() throws IOException {
        Path file = write("notes.md", """
                # Real Title

                ```java
                # not a heading
                ```
                """);

        ParsedDocument document = parser.parse(
                file,
                new SourceDocument(
                        "notes.md",
                        "RELEASE_NOTES",
                        "3.0",
                        "https://example.com/notes"
                )
        );

        assertEquals(1, document.sections().size());
        assertEquals("Real Title", document.title());
        assertTrue(document.sections().get(0).text().contains("# not a heading"));
    }

    @Test
    void fallsBackToFileNameWhenNoHeadingExists() throws IOException {
        Path file = write("plain.md", "Just some paragraph text.");

        ParsedDocument document = parser.parse(
                file,
                new SourceDocument(
                        "plain.md",
                        "RELEASE_NOTES",
                        null,
                        ""
                )
        );

        assertEquals("plain.md", document.title());
        assertEquals(1, document.sections().size());
    }

    private Path write(String name, String content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}
