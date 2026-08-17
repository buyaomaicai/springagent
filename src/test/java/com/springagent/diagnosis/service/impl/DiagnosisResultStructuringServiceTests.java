package com.springagent.diagnosis.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springagent.diagnosis.domain.dto.result.DiagnosisResult;
import com.springagent.parser.DiagnosisResultParser;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

/**
 * 验证结构化服务的 evidence 校正逻辑：事实字段必须来自服务端检索到的文档，
 * 模型回显的 URL/标题不被采信，越界编号和无来源 URL 的条目被丢弃。
 */
class DiagnosisResultStructuringServiceTests {

    private ValidatorFactory validatorFactory;
    private DiagnosisResultStructuringService service;

    @BeforeEach
    void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        service = new DiagnosisResultStructuringService(
                new DiagnosisResultParser(
                        new ObjectMapper(),
                        validatorFactory.getValidator()
                )
        );
    }

    @AfterEach
    void tearDown() {
        validatorFactory.close();
    }

    @Test
    void replacesModelEchoedEvidenceWithServerDocumentMetadata() {
        Document doc = Document.builder()
                .text("Spring Boot 3.0 requires Java 17.\n"
                        + "Applications must migrate from javax to jakarta.")
                .metadata(Map.of(
                        "source_url", "https://example.com/guide",
                        "source_type", "MIGRATION_GUIDE",
                        "component", "spring-boot",
                        "target_version", "3.0"
                ))
                .score(0.85)
                .build();

        DiagnosisResult result = service.structure(modelOutput(), List.of(doc));

        assertEquals(1, result.evidence().size());
        assertEquals(0, result.evidence().get(0).refIndex());
        assertEquals(
                "https://example.com/guide",
                result.evidence().get(0).sourceUrl()
        );
        assertEquals(
                "Spring Boot 3.0 requires Java 17.",
                result.evidence().get(0).title()
        );
        assertEquals(
                "MIGRATION_GUIDE",
                result.evidence().get(0).sourceType()
        );
        assertEquals("spring-boot", result.evidence().get(0).component());
        assertEquals("3.0", result.evidence().get(0).versionRange());
        assertEquals(
                0,
                new BigDecimal("0.85")
                        .compareTo(result.evidence().get(0).relevance())
        );
        assertTrue(result.evidence().get(0).excerpt()
                .startsWith("Spring Boot 3.0 requires Java 17."));
    }

    @Test
    void dropsEvidenceWhoseRefIndexIsOutOfRange() {
        Document doc = Document.builder()
                .text("Only one document")
                .metadata(Map.of("source_url", "https://example.com/guide"))
                .build();
        String output = modelOutput()
                .replace("\"refIndex\": 0", "\"refIndex\": 3");

        DiagnosisResult result = service.structure(output, List.of(doc));

        assertTrue(result.evidence().isEmpty());
    }

    @Test
    void dropsEvidenceWhenRetrievedDocumentHasNoSourceUrl() {
        Document doc = Document.builder()
                .text("A document without a source url")
                .metadata(Map.of("source_type", "MIGRATION_GUIDE"))
                .build();

        DiagnosisResult result = service.structure(modelOutput(), List.of(doc));

        assertTrue(result.evidence().isEmpty());
    }

    /**
     * 模型输出中声明了 evidence，但 sourceUrl/title 是编造的，
     * 结构化服务必须用服务端文档覆盖它们。
     */
    private String modelOutput() {
        return """
                {
                  "summary": "Upgrade is feasible",
                  "target": {
                    "javaVersion": "17",
                    "springBootVersion": "3.2.0"
                  },
                  "risks": [],
                  "compatibilityIssues": [],
                  "suggestions": [],
                  "planSteps": [],
                  "evidence": [{
                    "refIndex": 0,
                    "sourceUrl": "https://fabricated.example.com/not-real",
                    "title": "Fabricated title"
                  }]
                }
                """;
    }
}
