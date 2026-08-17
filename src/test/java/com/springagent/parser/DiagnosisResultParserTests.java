package com.springagent.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springagent.common.exception.DiagnosisResultParseException;
import com.springagent.diagnosis.domain.constant.ModificationActionType;
import com.springagent.diagnosis.domain.constant.RiskSeverity;
import com.springagent.diagnosis.domain.constant.SuggestionPriority;
import com.springagent.diagnosis.domain.constant.UpgradePhase;
import com.springagent.diagnosis.domain.dto.result.DiagnosisResult;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DiagnosisResultParserTests {

    private ValidatorFactory validatorFactory;
    private DiagnosisResultParser parser;

    @BeforeEach
    void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        parser = new DiagnosisResultParser(
                new ObjectMapper(),
                validatorFactory.getValidator()
        );
    }

    @AfterEach
    void tearDown() {
        validatorFactory.close();
    }

    @Test
    void parsesValidStructuredResult() {
        DiagnosisResult result = parser.parse(resultWithAllSections());

        assertEquals("Upgrade is feasible", result.summary());
        assertEquals("17", result.target().javaVersion());
        assertEquals("3.2.0", result.target().springBootVersion());
        assertEquals(RiskSeverity.HIGH, result.risks().get(0).severity());
        assertEquals(
                RiskSeverity.MEDIUM,
                result.compatibilityIssues().get(0).severity()
        );
        assertEquals(
                SuggestionPriority.P1,
                result.suggestions().get(0).priority()
        );
        assertEquals(
                ModificationActionType.DEPENDENCY,
                result.suggestions().get(0).actionType()
        );
        assertEquals(1, result.planSteps().get(0).sequenceNo());
        assertEquals(
                UpgradePhase.PREPARATION,
                result.planSteps().get(0).phase()
        );
        assertEquals(0, result.evidence().get(0).refIndex());
        assertEquals(
                "https://github.com/spring-projects/spring-boot/wiki/"
                        + "Spring-Boot-3.0-Migration-Guide",
                result.evidence().get(0).sourceUrl()
        );
    }

    @Test
    void parsesMultipleEvidenceReferences() {
        String json = resultWithAllSections().replace(
                "\"title\": \"Spring-Boot-3.0-Migration-Guide\"",
                "\"title\": \"Spring-Boot-3.0-Migration-Guide\"},"
                        + "{\"refIndex\": 2,"
                        + "\"sourceUrl\": \"https://example.com/notes\","
                        + "\"title\": \"Release Notes\""
        );

        DiagnosisResult result = parser.parse(json);

        assertEquals(2, result.evidence().size());
        assertEquals(2, result.evidence().get(1).refIndex());
        assertEquals("Release Notes", result.evidence().get(1).title());
    }

    @Test
    void rejectsNegativeEvidenceRefIndex() {
        String json = resultWithAllSections()
                .replace("\"refIndex\": 0", "\"refIndex\": -1");

        DiagnosisResultParseException error = assertThrows(
                DiagnosisResultParseException.class,
                () -> parser.parse(json)
        );

        assertTrue(error.getMessage().contains("evidence[0].refIndex"));
    }

    @Test
    void rejectsEvidenceWithoutSourceUrl() {
        String json = resultWithAllSections().replace(
                "\"sourceUrl\": \"https://github.com/spring-projects/"
                        + "spring-boot/wiki/Spring-Boot-3.0-Migration-Guide\"",
                "\"sourceUrl\": \"  \""
        );

        DiagnosisResultParseException error = assertThrows(
                DiagnosisResultParseException.class,
                () -> parser.parse(json)
        );

        assertTrue(error.getMessage().contains("evidence[0].sourceUrl"));
    }

    @Test
    void parsesJsonWrappedInMarkdownFenceWithBomAndCrLf() {
        String modelOutput = "\uFEFF```JSON\r\n"
                + resultWithEmptyArrays()
                + "\r\n```";

        DiagnosisResult result = parser.parse(modelOutput);

        assertEquals("No blocking issues", result.summary());
        assertTrue(result.risks().isEmpty());
    }

    @Test
    void allowsEmptyResultArrays() {
        DiagnosisResult result = parser.parse(resultWithEmptyArrays());

        assertTrue(result.risks().isEmpty());
        assertTrue(result.compatibilityIssues().isEmpty());
        assertTrue(result.suggestions().isEmpty());
        assertTrue(result.planSteps().isEmpty());
    }

    @Test
    void ignoresUnknownFieldsAtEveryLevel() {
        String json = """
                {
                  "summary": "No blocking issues",
                  "unknownRoot": "ignored",
                  "target": {
                    "javaVersion": "17",
                    "springBootVersion": "3.2.0",
                    "unknownTarget": true
                  },
                  "risks": [],
                  "compatibilityIssues": [],
                  "suggestions": [],
                  "planSteps": [],
                  "evidence": []
                }
                """;

        DiagnosisResult result = parser.parse(json);

        assertEquals("17", result.target().javaVersion());
    }

    @Test
    void rejectsMalformedJson() {
        DiagnosisResultParseException error = assertThrows(
                DiagnosisResultParseException.class,
                () -> parser.parse("{\"summary\":")
        );

        assertTrue(error.getMessage().contains("不是合法 JSON"));
    }

    @Test
    void rejectsMissingRequiredField() {
        String json = """
                {
                  "target": {
                    "javaVersion": "17",
                    "springBootVersion": "3.2.0"
                  },
                  "risks": [],
                  "compatibilityIssues": [],
                  "suggestions": [],
                  "planSteps": []
                }
                """;

        DiagnosisResultParseException error = assertThrows(
                DiagnosisResultParseException.class,
                () -> parser.parse(json)
        );

        assertTrue(error.getMessage().contains("summary"));
    }

    @Test
    void rejectsInvalidRiskSeverity() {
        String json = resultWithAllSections()
                .replace("\"HIGH\"", "\"VERY_HIGH\"");

        DiagnosisResultParseException error = assertThrows(
                DiagnosisResultParseException.class,
                () -> parser.parse(json)
        );

        assertTrue(error.getMessage().contains("枚举值不合法"));
        assertFalse(error.getCause() == null);
    }

    @Test
    void rejectsBlankNestedRequiredField() {
        String json = resultWithAllSections()
                .replace("\"Dependency conflict\"", "\"   \"");

        DiagnosisResultParseException error = assertThrows(
                DiagnosisResultParseException.class,
                () -> parser.parse(json)
        );

        assertTrue(error.getMessage().contains("risks[0].title"));
    }

    @Test
    void rejectsInvalidUpgradePhase() {
        String json = resultWithAllSections()
                .replace("\"PREPARATION\"", "\"ANALYSIS\"");

        DiagnosisResultParseException error = assertThrows(
                DiagnosisResultParseException.class,
                () -> parser.parse(json)
        );

        assertTrue(error.getMessage().contains("枚举值不合法"));
    }

    @Test
    void rejectsNonPositivePlanStepSequence() {
        String json = resultWithAllSections()
                .replace("\"sequenceNo\": 1", "\"sequenceNo\": 0");

        DiagnosisResultParseException error = assertThrows(
                DiagnosisResultParseException.class,
                () -> parser.parse(json)
        );

        assertTrue(error.getMessage().contains("planSteps[0].sequenceNo"));
    }

    @Test
    void rejectsTrailingContentAndNonObjectRoot() {
        assertThrows(
                DiagnosisResultParseException.class,
                () -> parser.parse(resultWithEmptyArrays() + " trailing")
        );
        DiagnosisResultParseException rootError = assertThrows(
                DiagnosisResultParseException.class,
                () -> parser.parse("[]")
        );
        assertTrue(rootError.getMessage().contains("根节点"));
    }

    private String resultWithEmptyArrays() {
        return """
                {
                  "summary": "No blocking issues",
                  "target": {
                    "javaVersion": "17",
                    "springBootVersion": "3.2.0"
                  },
                  "risks": [],
                  "compatibilityIssues": [],
                  "suggestions": [],
                  "planSteps": [],
                  "evidence": []
                }
                """;
    }

    private String resultWithAllSections() {
        return """
                {
                  "summary": "Upgrade is feasible",
                  "target": {
                    "javaVersion": "17",
                    "springBootVersion": "3.2.0"
                  },
                  "risks": [{
                    "category": "DEPENDENCY",
                    "severity": "HIGH",
                    "title": "Dependency conflict",
                    "description": "A library is incompatible",
                    "mitigation": "Upgrade the library"
                  }],
                  "compatibilityIssues": [{
                    "component": "Spring Security",
                    "issueType": "API_CHANGE",
                    "severity": "MEDIUM",
                    "currentVersion": "5.7",
                    "targetVersion": "6.2",
                    "symptom": "Compilation failure",
                    "rootCause": "Removed adapter API",
                    "confirmed": true
                  }],
                  "suggestions": [{
                    "priority": "P1",
                    "actionType": "DEPENDENCY",
                    "filePath": "pom.xml",
                    "title": "Upgrade dependencies",
                    "description": "Align dependencies with the Boot BOM",
                    "verification": "Run mvn test"
                  }],
                  "planSteps": [{
                    "sequenceNo": 1,
                    "phase": "PREPARATION",
                    "title": "Update the build",
                    "description": "Change the parent version",
                    "prerequisites": [],
                    "verification": "Run mvn test",
                    "rollbackAction": "Restore pom.xml",
                    "estimatedEffort": "30 minutes"
                  }],
                  "evidence": [{
                    "refIndex": 0,
                    "sourceUrl": "https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.0-Migration-Guide",
                    "title": "Spring-Boot-3.0-Migration-Guide"
                  }]
                }
                """;
    }
}
