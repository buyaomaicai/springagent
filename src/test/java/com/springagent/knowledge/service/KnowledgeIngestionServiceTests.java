package com.springagent.knowledge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.springagent.knowledge.chunking.ChunkingStrategy;
import com.springagent.knowledge.chunking.FixedSizeChunkingStrategy;
import com.springagent.knowledge.chunking.KnowledgeChunkingProperties;
import com.springagent.knowledge.parser.AsciidocDocumentParser;
import com.springagent.knowledge.source.SourceDefinition;
import com.springagent.knowledge.source.SourceDocument;
import com.springagent.knowledge.source.SourceRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

@ExtendWith(MockitoExtension.class)
class KnowledgeIngestionServiceTests {

    @TempDir
    Path tempDir;

    @Mock
    private SourceRegistry sourceRegistry;

    @Mock
    private VectorStore vectorStore;

    @Test
    void ingestsAllDocumentsWithMetadataAndIdempotentDelete()
            throws IOException {
        Path file = tempDir.resolve("guide.asciidoc");
        Files.writeString(file, longAsciidocContent(), StandardCharsets.UTF_8);
        SourceDefinition source = new SourceDefinition(
                "spring-boot-wiki",
                "spring-boot",
                "en",
                tempDir,
                List.of(new SourceDocument(
                        "guide.asciidoc",
                        "MIGRATION_GUIDE",
                        "3.0",
                        "https://example.com/guide"
                ))
        );
        when(sourceRegistry.load()).thenReturn(List.of(source));

        KnowledgeIngestionService service = new KnowledgeIngestionService(
                sourceRegistry,
                vectorStore,
                List.of(new AsciidocDocumentParser()),
                List.of(fixedStrategy()),
                fixedProperties()
        );

        int count = service.ingestAll();

        assertTrue(count > 0);
        verify(vectorStore).delete(any(Filter.Expression.class));

        ArgumentCaptor<List<Document>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        Document first = captor.getValue().get(0);
        assertEquals("spring-boot-wiki", first.getMetadata().get("source_id"));
        assertEquals("MIGRATION_GUIDE", first.getMetadata().get("source_type"));
        assertEquals("spring-boot", first.getMetadata().get("component"));
        assertEquals("en", first.getMetadata().get("language"));
        assertEquals("3.0", first.getMetadata().get("target_version"));
        assertEquals(
                "https://example.com/guide",
                first.getMetadata().get("source_url")
        );
        assertNotNull(first.getMetadata().get("chunk_index"));
        assertNotNull(first.getMetadata().get("content_hash"));
        assertNotNull(first.getId());
    }

    @Test
    void skipsMissingDocumentsWithoutTouchingVectorStore() {
        SourceDefinition source = new SourceDefinition(
                "missing-source",
                "jdk",
                "en",
                tempDir,
                List.of(new SourceDocument(
                        "not-there.html",
                        "JDK_DOC",
                        "17",
                        "https://example.com/not-there"
                ))
        );
        when(sourceRegistry.load()).thenReturn(List.of(source));

        KnowledgeIngestionService service = new KnowledgeIngestionService(
                sourceRegistry,
                vectorStore,
                List.of(new AsciidocDocumentParser()),
                List.of(fixedStrategy()),
                fixedProperties()
        );

        int count = service.ingestAll();

        assertEquals(0, count);
        verify(vectorStore, never()).delete(any(Filter.Expression.class));
        verify(vectorStore, never()).add(any());
    }

    private ChunkingStrategy fixedStrategy() {
        return new FixedSizeChunkingStrategy(fixedProperties());
    }

    private KnowledgeChunkingProperties fixedProperties() {
        KnowledgeChunkingProperties properties =
                new KnowledgeChunkingProperties();
        properties.setStrategy("FIXED");
        properties.setChunkSize(200);
        properties.setOverlap(20);
        return properties;
    }

    private String longAsciidocContent() {
        return """
                = Spring Boot 3.0 Migration Guide

                This guide explains how to migrate an existing Spring Boot 2.7
                application to Spring Boot 3.0. The most important change is
                the move from the javax namespace to the jakarta namespace for
                all Java EE APIs, including servlets, validation, persistence
                and expression language.

                == Jakarta namespace

                Replace all imports of javax.servlet, javax.validation and
                javax.persistence with the jakarta equivalents. Update Maven
                dependencies that still point to the old namespace and make
                sure the build compiles after the change.

                == Configuration properties

                Several configuration properties were renamed or removed in
                Spring Boot 3.0. Review the configuration changelog before
                upgrading and update application.properties accordingly.
                """;
    }
}
