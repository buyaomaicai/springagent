package com.springagent.diagnosis.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springagent.common.api.PageResponse;
import com.springagent.common.api.ErrorCode;
import com.springagent.common.exception.BusinessException;
import com.springagent.diagnosis.domain.constant.ModificationActionType;
import com.springagent.diagnosis.domain.constant.RiskSeverity;
import com.springagent.diagnosis.domain.constant.SuggestionPriority;
import com.springagent.diagnosis.domain.constant.UpgradePhase;
import com.springagent.diagnosis.domain.dto.response.CompatibilityIssueResponse;
import com.springagent.diagnosis.domain.dto.response.DiagnosisResultResponse;
import com.springagent.diagnosis.domain.dto.response.DiagnosisRiskResponse;
import com.springagent.diagnosis.domain.dto.response.DiagnosisRunSummaryResponse;
import com.springagent.diagnosis.domain.dto.response.KnowledgeEvidenceResponse;
import com.springagent.diagnosis.domain.dto.response.ModificationSuggestionResponse;
import com.springagent.diagnosis.domain.dto.response.UpgradePlanStepResponse;
import com.springagent.diagnosis.domain.dto.response.UpgradeTargetResponse;
import com.springagent.diagnosis.entity.CompatibilityIssue;
import com.springagent.diagnosis.entity.DiagnosisRisk;
import com.springagent.diagnosis.entity.DiagnosisRun;
import com.springagent.diagnosis.entity.KnowledgeEvidence;
import com.springagent.diagnosis.entity.ModificationSuggestion;
import com.springagent.diagnosis.entity.UpgradePlanStep;
import com.springagent.diagnosis.mapper.CompatibilityIssueMapper;
import com.springagent.diagnosis.mapper.DiagnosisRiskMapper;
import com.springagent.diagnosis.mapper.DiagnosisRunMapper;
import com.springagent.diagnosis.mapper.KnowledgeEvidenceMapper;
import com.springagent.diagnosis.mapper.ModificationSuggestionMapper;
import com.springagent.diagnosis.mapper.UpgradePlanStepMapper;
import com.springagent.diagnosis.model.DiagnosisRunStatus;
import com.springagent.diagnosis.service.IDiagnosisResultQueryService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 根据 diagnosis_id 读取一次诊断的主记录和全部结构化明细。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiagnosisResultQueryService
        implements IDiagnosisResultQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private static final TypeReference<List<String>> STRING_LIST_TYPE =
            new TypeReference<>() {
            };

    private final DiagnosisRunMapper diagnosisRunMapper;
    private final DiagnosisRiskMapper diagnosisRiskMapper;
    private final CompatibilityIssueMapper compatibilityIssueMapper;
    private final ModificationSuggestionMapper modificationSuggestionMapper;
    private final UpgradePlanStepMapper upgradePlanStepMapper;
    private final KnowledgeEvidenceMapper knowledgeEvidenceMapper;
    private final ObjectMapper objectMapper;

    /**
     * 只查询历史列表需要的诊断主记录，结果按创建时间和 ID 倒序排列。
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<DiagnosisRunSummaryResponse> listRuns(
            UUID conversationId,
            int page,
            int size,
            DiagnosisRunStatus status
    ) {
        requireValidPage(conversationId, page, size);

        Page<DiagnosisRun> queryPage = new Page<>(page, size);
        Page<DiagnosisRun> resultPage = diagnosisRunMapper.selectPage(
                queryPage,
                Wrappers.lambdaQuery(DiagnosisRun.class)
                        .eq(
                                DiagnosisRun::getConversationId,
                                conversationId
                        )
                        .eq(
                                status != null,
                                DiagnosisRun::getStatus,
                                status == null ? null : status.name()
                        )
                        .orderByDesc(DiagnosisRun::getCreatedAt)
                        .orderByDesc(DiagnosisRun::getId)
        );

        List<DiagnosisRunSummaryResponse> items = resultPage.getRecords()
                .stream()
                .map(this::mapRunSummary)
                .toList();
        return new PageResponse<>(
                items,
                resultPage.getTotal(),
                Math.toIntExact(resultPage.getCurrent()),
                Math.toIntExact(resultPage.getSize()),
                resultPage.getPages()
        );
    }

    /**
     * 先检查主记录状态，再读取明细。保存端在一个事务中写入明细并切换为
     * SUCCEEDED，因此查询端只要看到了 SUCCEEDED，就能读取到同一次提交的全部数据。
     */
    @Override
    @Transactional(readOnly = true)
    public DiagnosisResultResponse getResult(UUID diagnosisId) {
        DiagnosisRun diagnosisRun = diagnosisRunMapper.selectById(diagnosisId);
        if (diagnosisRun == null) {
            throw new BusinessException(ErrorCode.DIAGNOSIS_RUN_NOT_FOUND);
        }
        requireSucceeded(diagnosisRun);

        List<DiagnosisRisk> risks = diagnosisRiskMapper.selectList(
                Wrappers.lambdaQuery(DiagnosisRisk.class)
                        .eq(DiagnosisRisk::getDiagnosisId, diagnosisId)
                        .orderByAsc(DiagnosisRisk::getSortOrder)
        );
        List<CompatibilityIssue> compatibilityIssues =
                compatibilityIssueMapper.selectList(
                        Wrappers.lambdaQuery(CompatibilityIssue.class)
                                .eq(
                                        CompatibilityIssue::getDiagnosisId,
                                        diagnosisId
                                )
                                .orderByAsc(
                                        CompatibilityIssue::getSortOrder
                                )
                );
        List<ModificationSuggestion> suggestions =
                modificationSuggestionMapper.selectList(
                        Wrappers.lambdaQuery(ModificationSuggestion.class)
                                .eq(
                                        ModificationSuggestion::getDiagnosisId,
                                        diagnosisId
                                )
                                .orderByAsc(
                                        ModificationSuggestion::getSortOrder
                                )
                );
        List<UpgradePlanStep> planSteps = upgradePlanStepMapper.selectList(
                Wrappers.lambdaQuery(UpgradePlanStep.class)
                        .eq(UpgradePlanStep::getDiagnosisId, diagnosisId)
                        .orderByAsc(UpgradePlanStep::getSequenceNo)
        );
        List<KnowledgeEvidence> evidence = knowledgeEvidenceMapper.selectList(
                Wrappers.lambdaQuery(KnowledgeEvidence.class)
                        .eq(KnowledgeEvidence::getDiagnosisId, diagnosisId)
                        .orderByDesc(KnowledgeEvidence::getRelevance)
        );

        try {
            return new DiagnosisResultResponse(
                    diagnosisId,
                    requireSummary(diagnosisRun),
                    mapTarget(diagnosisRun.getTargetSnapshot()),
                    risks.stream().map(this::mapRisk).toList(),
                    compatibilityIssues.stream()
                            .map(this::mapCompatibilityIssue)
                            .toList(),
                    suggestions.stream().map(this::mapSuggestion).toList(),
                    planSteps.stream().map(this::mapPlanStep).toList(),
                    evidence.stream().map(this::mapEvidence).toList()
            );
        } catch (RuntimeException error) {
            log.error(
                    "Stored diagnosis result is invalid, diagnosisId={}",
                    diagnosisId,
                    error
            );
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private void requireSucceeded(DiagnosisRun diagnosisRun) {
        String status = diagnosisRun.getStatus();
        if (DiagnosisRunStatus.SUCCEEDED.name().equals(status)) {
            return;
        }
        if (DiagnosisRunStatus.QUEUED.name().equals(status)
                || DiagnosisRunStatus.RUNNING.name().equals(status)) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "诊断尚未完成"
            );
        }
        if (DiagnosisRunStatus.FAILED.name().equals(status)) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "诊断执行失败，无法获取结构化结果"
            );
        }
        if (DiagnosisRunStatus.CANCELLED.name().equals(status)) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "诊断已取消，无法获取结构化结果"
            );
        }

        log.error(
                "Diagnosis run has invalid status, diagnosisId={}, status={}",
                diagnosisRun.getId(),
                status
        );
        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private void requireValidPage(
            UUID conversationId,
            int page,
            int size
    ) {
        if (conversationId == null
                || page < 1
                || size < 1
                || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private DiagnosisRunSummaryResponse mapRunSummary(DiagnosisRun source) {
        DiagnosisRunStatus status;
        try {
            status = DiagnosisRunStatus.valueOf(source.getStatus());
        } catch (RuntimeException error) {
            log.error(
                    "Diagnosis run has invalid status, diagnosisId={}, status={}",
                    source.getId(),
                    source.getStatus(),
                    error
            );
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        return new DiagnosisRunSummaryResponse(
                source.getId(),
                source.getConversationId(),
                source.getQuestion(),
                status,
                source.getSummary(),
                mapOptionalTarget(source.getTargetSnapshot()),
                source.getErrorCode(),
                source.getStartedAt(),
                source.getCompletedAt(),
                source.getCreatedAt()
        );
    }

    private UpgradeTargetResponse mapOptionalTarget(JsonNode targetSnapshot) {
        if (targetSnapshot == null || !targetSnapshot.isObject()) {
            return null;
        }

        String javaVersion = textOrNull(targetSnapshot, "javaVersion");
        String springBootVersion = textOrNull(
                targetSnapshot,
                "springBootVersion"
        );
        if (javaVersion == null && springBootVersion == null) {
            return null;
        }
        return new UpgradeTargetResponse(javaVersion, springBootVersion);
    }

    private String textOrNull(JsonNode object, String fieldName) {
        JsonNode value = object.get(fieldName);
        if (value == null || value.isNull() || !value.isValueNode()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private String requireSummary(DiagnosisRun diagnosisRun) {
        String summary = diagnosisRun.getSummary();
        if (summary == null || summary.isBlank()) {
            throw new IllegalStateException("诊断摘要缺失");
        }
        return summary;
    }

    private UpgradeTargetResponse mapTarget(JsonNode targetSnapshot) {
        if (targetSnapshot == null || !targetSnapshot.isObject()) {
            throw new IllegalStateException("诊断目标快照缺失或格式错误");
        }
        UpgradeTargetResponse target = objectMapper.convertValue(
                targetSnapshot,
                UpgradeTargetResponse.class
        );
        if (target == null
                || target.javaVersion() == null
                || target.javaVersion().isBlank()
                || target.springBootVersion() == null
                || target.springBootVersion().isBlank()) {
            throw new IllegalStateException("诊断目标版本缺失");
        }
        return target;
    }

    private DiagnosisRiskResponse mapRisk(DiagnosisRisk source) {
        return new DiagnosisRiskResponse(
                source.getId(),
                source.getCategory(),
                RiskSeverity.valueOf(source.getSeverity()),
                source.getLikelihood(),
                source.getImpact(),
                source.getAffectedComponent(),
                source.getTitle(),
                source.getDescription(),
                source.getMitigation(),
                source.getSortOrder()
        );
    }

    private CompatibilityIssueResponse mapCompatibilityIssue(
            CompatibilityIssue source
    ) {
        return new CompatibilityIssueResponse(
                source.getId(),
                source.getComponent(),
                source.getIssueType(),
                RiskSeverity.valueOf(source.getSeverity()),
                source.getCurrentVersion(),
                source.getTargetVersion(),
                source.getSymptom(),
                source.getRootCause(),
                source.getConfirmed(),
                source.getSortOrder()
        );
    }

    private ModificationSuggestionResponse mapSuggestion(
            ModificationSuggestion source
    ) {
        return new ModificationSuggestionResponse(
                source.getId(),
                SuggestionPriority.valueOf(source.getPriority()),
                ModificationActionType.valueOf(source.getActionType()),
                source.getFilePath(),
                source.getTitle(),
                source.getDescription(),
                source.getBeforeContent(),
                source.getAfterContent(),
                source.getVerification(),
                source.getStatus(),
                source.getSortOrder()
        );
    }

    private UpgradePlanStepResponse mapPlanStep(UpgradePlanStep source) {
        JsonNode prerequisites = source.getPrerequisites();
        if (prerequisites == null || !prerequisites.isArray()) {
            throw new IllegalStateException("升级步骤 prerequisites 格式错误");
        }
        return new UpgradePlanStepResponse(
                source.getId(),
                source.getSequenceNo(),
                UpgradePhase.valueOf(source.getPhase()),
                source.getTitle(),
                source.getDescription(),
                objectMapper.convertValue(prerequisites, STRING_LIST_TYPE),
                source.getVerification(),
                source.getRollbackAction(),
                source.getEstimatedEffort(),
                source.getStatus()
        );
    }

    private KnowledgeEvidenceResponse mapEvidence(KnowledgeEvidence source) {
        return new KnowledgeEvidenceResponse(
                source.getId(),
                source.getSourceType(),
                source.getSourceUrl(),
                source.getTitle(),
                source.getComponent(),
                source.getVersionRange(),
                source.getExcerpt(),
                source.getRelevance()
        );
    }
}
