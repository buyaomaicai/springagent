package com.springagent.diagnosis.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springagent.common.api.ErrorCode;
import com.springagent.common.api.PageResponse;
import com.springagent.common.exception.BusinessException;
import com.springagent.common.persistence.JsonbTypeHandler;
import com.springagent.common.persistence.UuidTypeHandler;
import com.springagent.diagnosis.domain.dto.response.DiagnosisResultResponse;
import com.springagent.diagnosis.domain.dto.response.DiagnosisRunSummaryResponse;
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
import com.springagent.diagnosis.model.DiagnosisRunStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiagnosisResultQueryServiceTests {

    private static final UUID DIAGNOSIS_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000201"
    );
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
                "diagnosis-result-query-tests"
        );
        TableInfoHelper.initTableInfo(assistant, DiagnosisRun.class);
        TableInfoHelper.initTableInfo(assistant, DiagnosisRisk.class);
        TableInfoHelper.initTableInfo(assistant, CompatibilityIssue.class);
        TableInfoHelper.initTableInfo(assistant, ModificationSuggestion.class);
        TableInfoHelper.initTableInfo(assistant, UpgradePlanStep.class);
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

    private DiagnosisResultQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new DiagnosisResultQueryService(
                diagnosisRunMapper,
                diagnosisRiskMapper,
                compatibilityIssueMapper,
                modificationSuggestionMapper,
                upgradePlanStepMapper,
                OBJECT_MAPPER
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsPagedRunSummariesInRequestedConversation() {
        UUID conversationId = UUID.fromString(
                "00000000-0000-0000-0000-000000000101"
        );
        OffsetDateTime now = OffsetDateTime.parse(
                "2026-08-16T12:00:00Z"
        );
        DiagnosisRun succeeded = succeededRun()
                .setConversationId(conversationId)
                .setQuestion("Upgrade Spring Boot")
                .setCreatedAt(now)
                .setStartedAt(now.plusSeconds(1))
                .setCompletedAt(now.plusSeconds(10));
        DiagnosisRun failed = new DiagnosisRun()
                .setId(UUID.fromString(
                        "00000000-0000-0000-0000-000000000202"
                ))
                .setConversationId(conversationId)
                .setQuestion("Retry upgrade")
                .setStatus(DiagnosisRunStatus.FAILED.name())
                .setErrorCode("MODEL_STREAM_FAILED")
                .setTargetSnapshot(OBJECT_MAPPER.createObjectNode())
                .setCreatedAt(now.minusMinutes(1));

        Page<DiagnosisRun> storedPage = new Page<>(2, 2, 3);
        storedPage.setRecords(List.of(succeeded, failed));
        when(diagnosisRunMapper.selectPage(any(Page.class), any()))
                .thenReturn(storedPage);

        PageResponse<DiagnosisRunSummaryResponse> result =
                queryService.listRuns(
                        conversationId,
                        2,
                        2,
                        DiagnosisRunStatus.SUCCEEDED
                );

        assertEquals(2, result.page());
        assertEquals(2, result.size());
        assertEquals(3, result.total());
        assertEquals(2, result.totalPages());
        assertEquals(2, result.items().size());
        assertEquals(DIAGNOSIS_ID, result.items().get(0).diagnosisId());
        assertEquals("17", result.items().get(0).target().javaVersion());
        assertEquals(
                DiagnosisRunStatus.FAILED,
                result.items().get(1).status()
        );
        assertEquals(
                "MODEL_STREAM_FAILED",
                result.items().get(1).errorCode()
        );
        assertEquals(null, result.items().get(1).target());

        org.mockito.ArgumentCaptor<Page<DiagnosisRun>> pageCaptor =
                org.mockito.ArgumentCaptor.forClass(Page.class);
        org.mockito.ArgumentCaptor<LambdaQueryWrapper<DiagnosisRun>>
                wrapperCaptor =
                org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(diagnosisRunMapper).selectPage(
                pageCaptor.capture(),
                wrapperCaptor.capture()
        );
        assertEquals(2, pageCaptor.getValue().getCurrent());
        assertEquals(2, pageCaptor.getValue().getSize());

        LambdaQueryWrapper<DiagnosisRun> wrapper = wrapperCaptor.getValue();
        String sql = wrapper.getCustomSqlSegment();
        assertTrue(sql.contains("conversation_id"));
        assertTrue(sql.contains("status"));
        assertTrue(sql.contains("ORDER BY created_at DESC,id DESC"));
        assertTrue(wrapper.getParamNameValuePairs()
                .containsValue(conversationId));
        assertTrue(wrapper.getParamNameValuePairs()
                .containsValue(DiagnosisRunStatus.SUCCEEDED.name()));
    }

    @Test
    void returnsEmptyHistoryPage() {
        UUID conversationId = UUID.fromString(
                "00000000-0000-0000-0000-000000000101"
        );
        Page<DiagnosisRun> storedPage = new Page<>(1, 20, 0);
        storedPage.setRecords(List.of());
        when(diagnosisRunMapper.selectPage(any(Page.class), any()))
                .thenReturn(storedPage);

        PageResponse<DiagnosisRunSummaryResponse> result =
                queryService.listRuns(conversationId, 1, 20, null);

        assertEquals(List.of(), result.items());
        assertEquals(0, result.total());
        assertEquals(0, result.totalPages());
    }

    @Test
    void rejectsInvalidHistoryPageBeforeQueryingDatabase() {
        BusinessException error = assertThrows(
                BusinessException.class,
                () -> queryService.listRuns(
                        DIAGNOSIS_ID,
                        0,
                        20,
                        null
                )
        );

        assertEquals(ErrorCode.INVALID_REQUEST, error.getErrorCode());
        verify(diagnosisRunMapper, never()).selectPage(any(), any());
    }

    @Test
    void returnsCompleteResultFromRunAndDetailTables() {
        DiagnosisRun run = succeededRun();
        DiagnosisRisk risk = new DiagnosisRisk()
                .setDiagnosisId(DIAGNOSIS_ID)
                .setCategory("DEPENDENCY")
                .setSeverity("HIGH")
                .setLikelihood((short) 4)
                .setImpact((short) 5)
                .setAffectedComponent("spring-web")
                .setTitle("Dependency risk")
                .setDescription("A dependency is incompatible")
                .setMitigation("Upgrade it")
                .setSortOrder(0);
        CompatibilityIssue issue = new CompatibilityIssue()
                .setDiagnosisId(DIAGNOSIS_ID)
                .setComponent("spring-web")
                .setIssueType("REMOVED_API")
                .setSeverity("CRITICAL")
                .setCurrentVersion("5.3.0")
                .setTargetVersion("6.1.0")
                .setSymptom("Compilation fails")
                .setRootCause("API removed")
                .setConfirmed(true)
                .setSortOrder(0);
        ModificationSuggestion suggestion = new ModificationSuggestion()
                .setDiagnosisId(DIAGNOSIS_ID)
                .setPriority("P0")
                .setActionType("DEPENDENCY")
                .setFilePath("pom.xml")
                .setTitle("Upgrade dependency")
                .setDescription("Replace old dependency")
                .setVerification("Run mvn test")
                .setStatus("PROPOSED")
                .setSortOrder(0);
        UpgradePlanStep planStep = new UpgradePlanStep()
                .setDiagnosisId(DIAGNOSIS_ID)
                .setSequenceNo(1)
                .setPhase("BUILD")
                .setTitle("Upgrade build")
                .setDescription("Update parent version")
                .setPrerequisites(OBJECT_MAPPER.valueToTree(
                        List.of("backup project")
                ))
                .setVerification("Run mvn verify")
                .setRollbackAction("Restore old parent")
                .setEstimatedEffort("1 hour")
                .setStatus("PENDING");

        when(diagnosisRunMapper.selectById(DIAGNOSIS_ID)).thenReturn(run);
        when(diagnosisRiskMapper.selectList(any()))
                .thenReturn(List.of(risk));
        when(compatibilityIssueMapper.selectList(any()))
                .thenReturn(List.of(issue));
        when(modificationSuggestionMapper.selectList(any()))
                .thenReturn(List.of(suggestion));
        when(upgradePlanStepMapper.selectList(any()))
                .thenReturn(List.of(planStep));

        DiagnosisResultResponse result = queryService.getResult(DIAGNOSIS_ID);

        assertEquals(DIAGNOSIS_ID, result.diagnosisId());
        assertEquals("Upgrade is feasible", result.summary());
        assertEquals("17", result.target().javaVersion());
        assertEquals("3.2.0", result.target().springBootVersion());
        assertEquals("HIGH", result.risks().get(0).severity().name());
        assertEquals("spring-web", result.risks().get(0).affectedComponent());
        assertEquals(
                "CRITICAL",
                result.compatibilityIssues().get(0).severity().name()
        );
        assertEquals("P0", result.suggestions().get(0).priority().name());
        assertEquals("PROPOSED", result.suggestions().get(0).status());
        assertEquals("BUILD", result.planSteps().get(0).phase().name());
        assertEquals(
                List.of("backup project"),
                result.planSteps().get(0).prerequisites()
        );
    }

    @Test
    void rejectsResultQueryWhileRunIsStillRunning() {
        DiagnosisRun run = new DiagnosisRun()
                .setId(DIAGNOSIS_ID)
                .setStatus(DiagnosisRunStatus.RUNNING.name());
        when(diagnosisRunMapper.selectById(DIAGNOSIS_ID)).thenReturn(run);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> queryService.getResult(DIAGNOSIS_ID)
        );

        assertEquals(ErrorCode.CONFLICT, error.getErrorCode());
        assertEquals("诊断尚未完成", error.getMessage());
        verify(diagnosisRiskMapper, never()).selectList(any());
        verify(compatibilityIssueMapper, never()).selectList(any());
        verify(modificationSuggestionMapper, never()).selectList(any());
        verify(upgradePlanStepMapper, never()).selectList(any());
    }

    @Test
    void rejectsMissingDiagnosisRun() {
        when(diagnosisRunMapper.selectById(DIAGNOSIS_ID)).thenReturn(null);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> queryService.getResult(DIAGNOSIS_ID)
        );

        assertEquals(
                ErrorCode.DIAGNOSIS_RUN_NOT_FOUND,
                error.getErrorCode()
        );
    }

    @Test
    void hidesCorruptStoredResultAsInternalServerError() {
        DiagnosisRun run = succeededRun()
                .setTargetSnapshot(OBJECT_MAPPER.createObjectNode());
        when(diagnosisRunMapper.selectById(DIAGNOSIS_ID)).thenReturn(run);
        when(diagnosisRiskMapper.selectList(any())).thenReturn(List.of());
        when(compatibilityIssueMapper.selectList(any())).thenReturn(List.of());
        when(modificationSuggestionMapper.selectList(any()))
                .thenReturn(List.of());
        when(upgradePlanStepMapper.selectList(any())).thenReturn(List.of());

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> queryService.getResult(DIAGNOSIS_ID)
        );

        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, error.getErrorCode());
        assertEquals(
                ErrorCode.INTERNAL_SERVER_ERROR.defaultMessage(),
                error.getMessage()
        );
    }

    private DiagnosisRun succeededRun() {
        return new DiagnosisRun()
                .setId(DIAGNOSIS_ID)
                .setStatus(DiagnosisRunStatus.SUCCEEDED.name())
                .setSummary("Upgrade is feasible")
                .setTargetSnapshot(OBJECT_MAPPER.valueToTree(
                        new TargetFixture("17", "3.2.0")
                ));
    }

    private record TargetFixture(
            String javaVersion,
            String springBootVersion
    ) {
    }
}
