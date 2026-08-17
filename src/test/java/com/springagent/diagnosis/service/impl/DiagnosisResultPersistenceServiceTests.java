package com.springagent.diagnosis.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springagent.common.persistence.JsonbTypeHandler;
import com.springagent.common.persistence.UuidTypeHandler;
import com.springagent.diagnosis.domain.constant.ModificationActionType;
import com.springagent.diagnosis.domain.constant.RiskSeverity;
import com.springagent.diagnosis.domain.constant.SuggestionPriority;
import com.springagent.diagnosis.domain.constant.UpgradePhase;
import com.springagent.diagnosis.domain.dto.result.CompatibilityFinding;
import com.springagent.diagnosis.domain.dto.result.DiagnosisResult;
import com.springagent.diagnosis.domain.dto.result.RiskItem;
import com.springagent.diagnosis.domain.dto.result.SuggestedModification;
import com.springagent.diagnosis.domain.dto.result.UpgradePlanStepResult;
import com.springagent.diagnosis.domain.dto.result.UpgradeTarget;
import com.springagent.diagnosis.entity.CompatibilityIssue;
import com.springagent.diagnosis.entity.DiagnosisRisk;
import com.springagent.diagnosis.entity.DiagnosisRun;
import com.springagent.diagnosis.entity.ModificationSuggestion;
import com.springagent.diagnosis.entity.UpgradePlanStep;
import com.springagent.diagnosis.mapper.CompatibilityIssueMapper;
import com.springagent.diagnosis.mapper.DiagnosisRiskMapper;
import com.springagent.diagnosis.mapper.DiagnosisRunMapper;
import com.springagent.diagnosis.mapper.ModificationSuggestionMapper;
import com.springagent.diagnosis.mapper.UpgradePlanStepMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiagnosisResultPersistenceServiceTests {

    @BeforeAll
    static void initializeMyBatisTableMetadata() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.getTypeHandlerRegistry().register(
                UUID.class,
                UuidTypeHandler.class
        );
        configuration.getTypeHandlerRegistry().register(
                JsonNode.class,
                JsonbTypeHandler.class
        );
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                configuration,
                "diagnosis-result-persistence-tests"
        );
        TableInfoHelper.initTableInfo(assistant, DiagnosisRun.class);
    }

    @Mock
    private DiagnosisRunMapper diagnosisRunMapper;

    @Mock
    private DiagnosisRiskMapper diagnosisRiskMapper;

    @Mock
    private CompatibilityIssueMapper compatibilityIssueMapper;

    @Mock
    private ModificationSuggestionMapper modificationSuggestionMapper;

    @Mock
    private UpgradePlanStepMapper upgradePlanStepMapper;

    @Mock
    private DiagnosisRunLifecycleService diagnosisRunLifecycleService;

    private DiagnosisResultPersistenceService persistenceService;

    @BeforeEach
    void setUp() {
        persistenceService = new DiagnosisResultPersistenceService(
                diagnosisRunMapper,
                diagnosisRiskMapper,
                compatibilityIssueMapper,
                modificationSuggestionMapper,
                upgradePlanStepMapper,
                diagnosisRunLifecycleService,
                new ObjectMapper()
        );
    }

    @Test
    void mapsAndSavesCompleteStructuredResult() {
        DiagnosisRun run = runningRun();
        DiagnosisResult result = completeResult();
        OffsetDateTime completedAt = OffsetDateTime.now();
        when(diagnosisRunMapper.update(any(DiagnosisRun.class), any()))
                .thenReturn(1);
        when(diagnosisRiskMapper.insert(any(DiagnosisRisk.class)))
                .thenReturn(1);
        when(compatibilityIssueMapper.insert(any(CompatibilityIssue.class)))
                .thenReturn(1);
        when(modificationSuggestionMapper.insert(
                any(ModificationSuggestion.class)
        )).thenReturn(1);
        when(upgradePlanStepMapper.insert(any(UpgradePlanStep.class)))
                .thenReturn(1);

        persistenceService.save(
                run,
                result,
                "raw model output",
                completedAt
        );

        ArgumentCaptor<DiagnosisRun> runResultCaptor =
                ArgumentCaptor.forClass(DiagnosisRun.class);
        verify(diagnosisRunMapper).update(runResultCaptor.capture(), any());
        DiagnosisRun storedRunResult = runResultCaptor.getValue();
        assertEquals("Upgrade is feasible", storedRunResult.getSummary());
        assertEquals(
                "17",
                storedRunResult.getTargetSnapshot().get("javaVersion").asText()
        );
        assertEquals(
                "Upgrade is feasible",
                storedRunResult.getRawResult().get("summary").asText()
        );

        ArgumentCaptor<DiagnosisRisk> riskCaptor =
                ArgumentCaptor.forClass(DiagnosisRisk.class);
        verify(diagnosisRiskMapper).insert(riskCaptor.capture());
        DiagnosisRisk risk = riskCaptor.getValue();
        assertNotNull(risk.getId());
        assertEquals(run.getId(), risk.getDiagnosisId());
        assertEquals("HIGH", risk.getSeverity());
        assertEquals(0, risk.getSortOrder());

        ArgumentCaptor<CompatibilityIssue> issueCaptor =
                ArgumentCaptor.forClass(CompatibilityIssue.class);
        verify(compatibilityIssueMapper).insert(issueCaptor.capture());
        CompatibilityIssue issue = issueCaptor.getValue();
        assertEquals("spring-web", issue.getComponent());
        assertEquals("CRITICAL", issue.getSeverity());
        assertEquals(true, issue.getConfirmed());

        ArgumentCaptor<ModificationSuggestion> suggestionCaptor =
                ArgumentCaptor.forClass(ModificationSuggestion.class);
        verify(modificationSuggestionMapper).insert(
                suggestionCaptor.capture()
        );
        ModificationSuggestion suggestion = suggestionCaptor.getValue();
        assertEquals("P0", suggestion.getPriority());
        assertEquals("DEPENDENCY", suggestion.getActionType());
        assertEquals("PROPOSED", suggestion.getStatus());

        ArgumentCaptor<UpgradePlanStep> stepCaptor =
                ArgumentCaptor.forClass(UpgradePlanStep.class);
        verify(upgradePlanStepMapper).insert(stepCaptor.capture());
        UpgradePlanStep step = stepCaptor.getValue();
        assertEquals(1, step.getSequenceNo());
        assertEquals("BUILD", step.getPhase());
        assertEquals("PENDING", step.getStatus());
        assertEquals("upgrade parent", step.getPrerequisites().get(0).asText());

        verify(diagnosisRunLifecycleService).markSucceeded(
                run,
                "raw model output",
                completedAt
        );
    }

    @Test
    void acceptsEmptyDetailCollections() {
        DiagnosisRun run = runningRun();
        DiagnosisResult result = new DiagnosisResult(
                "No blocking issue",
                new UpgradeTarget("17", "3.2.0"),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
        OffsetDateTime completedAt = OffsetDateTime.now();
        when(diagnosisRunMapper.update(any(DiagnosisRun.class), any()))
                .thenReturn(1);

        persistenceService.save(run, result, "{}", completedAt);

        verify(diagnosisRiskMapper, never())
                .insert(any(DiagnosisRisk.class));
        verify(compatibilityIssueMapper, never())
                .insert(any(CompatibilityIssue.class));
        verify(modificationSuggestionMapper, never())
                .insert(any(ModificationSuggestion.class));
        verify(upgradePlanStepMapper, never())
                .insert(any(UpgradePlanStep.class));
        verify(diagnosisRunLifecycleService).markSucceeded(
                run,
                "{}",
                completedAt
        );
    }

    @Test
    void doesNotMarkRunSucceededWhenDetailInsertFails() {
        DiagnosisRun run = runningRun();
        DiagnosisResult result = completeResult();
        when(diagnosisRunMapper.update(any(DiagnosisRun.class), any()))
                .thenReturn(1);
        when(diagnosisRiskMapper.insert(any(DiagnosisRisk.class)))
                .thenReturn(0);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> persistenceService.save(
                        run,
                        result,
                        "raw model output",
                        OffsetDateTime.now()
                )
        );

        assertEquals("保存诊断风险失败", error.getMessage());
        verify(compatibilityIssueMapper, never())
                .insert(any(CompatibilityIssue.class));
        verify(modificationSuggestionMapper, never())
                .insert(any(ModificationSuggestion.class));
        verify(upgradePlanStepMapper, never())
                .insert(any(UpgradePlanStep.class));
        verify(diagnosisRunLifecycleService, never()).markSucceeded(
                any(),
                any(),
                any()
        );
    }

    private DiagnosisRun runningRun() {
        return new DiagnosisRun()
                .setId(UUID.randomUUID())
                .setResponseMessageId(UUID.randomUUID())
                .setStatus("RUNNING");
    }

    private DiagnosisResult completeResult() {
        return new DiagnosisResult(
                "Upgrade is feasible",
                new UpgradeTarget("17", "3.2.0"),
                List.of(new RiskItem(
                        "DEPENDENCY",
                        RiskSeverity.HIGH,
                        "Dependency risk",
                        "A dependency is incompatible",
                        "Upgrade the dependency"
                )),
                List.of(new CompatibilityFinding(
                        "spring-web",
                        "REMOVED_API",
                        RiskSeverity.CRITICAL,
                        "5.3.0",
                        "6.1.0",
                        "Compilation fails",
                        "The API was removed",
                        true
                )),
                List.of(new SuggestedModification(
                        SuggestionPriority.P0,
                        ModificationActionType.DEPENDENCY,
                        "pom.xml",
                        "Upgrade dependency",
                        "Replace the old dependency",
                        "1.0.0",
                        "2.0.0",
                        "Run mvn test"
                )),
                List.of(new UpgradePlanStepResult(
                        1,
                        UpgradePhase.BUILD,
                        "Upgrade build",
                        "Update the parent version",
                        List.of("upgrade parent"),
                        "Run mvn verify",
                        "Restore the old parent",
                        "1 hour"
                ))
        );
    }
}
