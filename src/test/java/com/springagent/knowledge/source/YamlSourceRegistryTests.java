package com.springagent.knowledge.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlSourceRegistryTests {

    @TempDir
    Path tempDir;

    private Path sourcesFile;
    private Path rawRoot;

    @BeforeEach
    void setUp() throws IOException {
        rawRoot = Files.createDirectories(tempDir.resolve("raw"));
        Files.createDirectories(rawRoot.resolve("jdk-17-migration"));
        Files.writeString(
                rawRoot.resolve("jdk-17-migration/index.html"),
                "<html><body><h1>JDK 17 Migration</h1></body></html>",
                StandardCharsets.UTF_8
        );
        Files.writeString(
                rawRoot.resolve("jdk-17-migration/getting-started.html"),
                "<html><body><h1>Getting Started</h1></body></html>",
                StandardCharsets.UTF_8
        );
        sourcesFile = tempDir.resolve("sources.yml");
        Files.writeString(sourcesFile, """
                schema_version: 1
                git_sources:
                  - id: spring-boot-wiki
                    component: spring-boot
                    language: en
                    default_source_type: RELEASE_NOTES
                    source_url_prefix: https://github.com/spring-projects/spring-boot/wiki/
                    documents:
                      - path: releasenotes/Spring-Boot-3.0-Migration-Guide.asciidoc
                        source_type: MIGRATION_GUIDE
                        target_version: "3.0"
                web_sources:
                  - id: jdk-17-migration
                    root_url: https://docs.oracle.com/en/java/javase/17/migrate/
                    component: jdk
                    language: en
                    source_type: JDK_DOC
                    target_version: "17"
                """, StandardCharsets.UTF_8);
    }

    @Test
    void loadsGitSourceWithExplicitMetadata() {
        YamlSourceRegistry registry = new YamlSourceRegistry(
                sourcesFile.toString(),
                rawRoot.toString()
        );

        List<SourceDefinition> definitions = registry.load();

        SourceDefinition git = definitions.stream()
                .filter(source -> source.id().equals("spring-boot-wiki"))
                .findFirst()
                .orElseThrow();
        assertEquals("spring-boot", git.component());
        assertEquals("en", git.language());
        assertEquals(1, git.documents().size());
        SourceDocument doc = git.documents().get(0);
        assertEquals("Spring-Boot-3.0-Migration-Guide.asciidoc", doc.fileName());
        assertEquals("MIGRATION_GUIDE", doc.sourceType());
        assertEquals("3.0", doc.targetVersion());
        assertEquals(
                "https://github.com/spring-projects/spring-boot/wiki/"
                        + "Spring-Boot-3.0-Migration-Guide",
                doc.sourceUrl()
        );
    }

    @Test
    void discoversWebSourceHtmlFilesWithJoinedSourceUrl() {
        YamlSourceRegistry registry = new YamlSourceRegistry(
                sourcesFile.toString(),
                rawRoot.toString()
        );

        List<SourceDefinition> definitions = registry.load();

        SourceDefinition web = definitions.stream()
                .filter(source -> source.id().equals("jdk-17-migration"))
                .findFirst()
                .orElseThrow();
        assertEquals("jdk", web.component());
        assertEquals("JDK_DOC", web.documents().get(0).sourceType());
        assertEquals("17", web.documents().get(0).targetVersion());
        assertEquals(2, web.documents().size());
        assertTrue(web.documents().stream()
                .allMatch(doc -> doc.sourceUrl()
                        .startsWith("https://docs.oracle.com/en/java/javase/17/migrate/")));
    }
}
