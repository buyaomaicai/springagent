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

class AsciidocDocumentParserTests {

    @TempDir
    Path tempDir;

    private final AsciidocDocumentParser parser = new AsciidocDocumentParser();

    @Test
    void splitsSectionsByHeadingLevels() throws IOException {
        Path file = write("guide.asciidoc", """
                = Spring Boot 3.0 Migration Guide

                Introduction text here.

                == Jakarta namespace

                javax to jakarta migration details.

                == Config properties

                Property renames happened.
                """);

        ParsedDocument document = parser.parse(
                file,
                new SourceDocument(
                        "guide.asciidoc",
                        "MIGRATION_GUIDE",
                        "3.0",
                        "https://example.com/guide"
                )
        );

        assertEquals("Spring Boot 3.0 Migration Guide", document.title());
        assertEquals(3, document.sections().size());
        assertEquals("Jakarta namespace", document.sections().get(1).title());
        assertEquals(2, document.sections().get(1).level());
        assertTrue(document.sections().get(1).text()
                .contains("javax to jakarta migration details"));
    }

    @Test
    void keepsAsciidocTableContentInSectionText() throws IOException {
        Path file = write("guide.asciidoc", """
                = Table Guide

                == Versions

                |===
                | Old | New
                | 2.7 | 3.0
                |===
                """);

        ParsedDocument document = parser.parse(
                file,
                new SourceDocument(
                        "guide.asciidoc",
                        "MIGRATION_GUIDE",
                        "3.0",
                        "https://example.com/guide"
                )
        );

        assertTrue(document.sections().get(0).text().contains("| 2.7 | 3.0"));
    }

    @Test
    void treatsBareEqualsLinesAsTextNotHeadings() throws IOException {
        Path file = write("guide.asciidoc", """
                == Section

                ====

                Not a heading but a block delimiter.

                == Next

                Real content of the next section.
                """);

        ParsedDocument document = parser.parse(
                file,
                new SourceDocument(
                        "guide.asciidoc",
                        "MIGRATION_GUIDE",
                        "3.0",
                        "https://example.com/guide"
                )
        );

        assertEquals(2, document.sections().size());
        assertEquals("Section", document.sections().get(0).title());
        assertEquals("Next", document.sections().get(1).title());
        assertTrue(document.sections().get(0).text()
                .contains("Not a heading"));
    }

    private Path write(String name, String content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}
