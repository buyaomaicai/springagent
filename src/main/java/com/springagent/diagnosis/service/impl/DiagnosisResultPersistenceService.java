package com.springagent.diagnosis.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springagent.diagnosis.domain.dto.result.CompatibilityFinding;
import com.springagent.diagnosis.domain.dto.result.DiagnosisResult;
import com.springagent.diagnosis.domain.dto.result.RiskItem;
import com.springagent.diagnosis.domain.dto.result.SuggestedModification;
import com.springagent.diagnosis.domain.dto.result.UpgradePlanStepResult;
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
import com.springagent.diagnosis.service.IDiagnosisResultPersistenceService;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 负责把一个完整的 {@link DiagnosisResult} 拆分到诊断主表和各类明细表。
 *
 * <p>模型调用不在这个事务中。只有模型流正常结束并且结果通过解析校验后，才会
 * 进入这个短事务。主表结果、风险、兼容性问题、修改建议、升级步骤、助手消息和
 * SUCCEEDED 状态会一起提交；任意一步失败都会整体回滚。</p>
 */
@Service
@RequiredArgsConstructor
public class DiagnosisResultPersistenceService
        implements IDiagnosisResultPersistenceService {

    private static final String SUGGESTION_INITIAL_STATUS = "PROPOSED";
    private static final String PLAN_STEP_INITIAL_STATUS = "PENDING";

    private final DiagnosisRunMapper diagnosisRunMapper;
    private final DiagnosisRiskMapper diagnosisRiskMapper;
    private final CompatibilityIssueMapper compatibilityIssueMapper;
    private final ModificationSuggestionMapper modificationSuggestionMapper;
    private final UpgradePlanStepMapper upgradePlanStepMapper;
    private final DiagnosisRunLifecycleService diagnosisRunLifecycleService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void save(
            DiagnosisRun diagnosisRun,
            DiagnosisResult result,
            String modelOutput,
            OffsetDateTime completedAt
    ) {
        updateRunResult(diagnosisRun, result);
        saveRisks(diagnosisRun, result);
        saveCompatibilityIssues(diagnosisRun, result);
        saveSuggestions(diagnosisRun, result);
        savePlanSteps(diagnosisRun, result);

        // markSucceeded 使用默认 REQUIRED 传播级别，因此会加入当前事务。
        diagnosisRunLifecycleService.markSucceeded(
                diagnosisRun,
                modelOutput,
                completedAt
        );
    }

    /**
     * 保存适合列表页直接读取的摘要、目标快照，以及完整的结构化 JSON 快照。
     * 模型原始文本由对应的助手消息保存，不放进 raw_result。
     */
    private void updateRunResult(
            DiagnosisRun diagnosisRun,
            DiagnosisResult result
    ) {
        DiagnosisRun structuredResult = new DiagnosisRun()
                .setSummary(result.summary())
                .setTargetSnapshot(objectMapper.valueToTree(result.target()))
                .setRawResult(objectMapper.valueToTree(result));

        LambdaUpdateWrapper<DiagnosisRun> update = Wrappers
                .lambdaUpdate(DiagnosisRun.class)
                .eq(DiagnosisRun::getId, diagnosisRun.getId())
                .eq(
                        DiagnosisRun::getStatus,
                        DiagnosisRunStatus.RUNNING.name()
                );

        requireOneRow(
                diagnosisRunMapper.update(structuredResult, update),
                "保存诊断运行结构化结果失败"
        );
    }

    private void saveRisks(
            DiagnosisRun diagnosisRun,
            DiagnosisResult result
    ) {
        for (int index = 0; index < result.risks().size(); index++) {
            RiskItem source = result.risks().get(index);
            DiagnosisRisk entity = new DiagnosisRisk()
                    .setDiagnosisId(diagnosisRun.getId())
                    .setCategory(source.category())
                    .setSeverity(source.severity().name())
                    .setTitle(source.title())
                    .setDescription(source.description())
                    .setMitigation(source.mitigation())
                    .setSortOrder(index);
            requireOneRow(
                    diagnosisRiskMapper.insert(entity),
                    "保存诊断风险失败"
            );
        }
    }

    private void saveCompatibilityIssues(
            DiagnosisRun diagnosisRun,
            DiagnosisResult result
    ) {
        for (int index = 0;
                index < result.compatibilityIssues().size();
                index++) {
            CompatibilityFinding source = result
                    .compatibilityIssues()
                    .get(index);
            CompatibilityIssue entity = new CompatibilityIssue()
                    .setDiagnosisId(diagnosisRun.getId())
                    .setComponent(source.component())
                    .setIssueType(source.issueType())
                    .setSeverity(source.severity().name())
                    .setCurrentVersion(source.currentVersion())
                    .setTargetVersion(source.targetVersion())
                    .setSymptom(source.symptom())
                    .setRootCause(source.rootCause())
                    .setConfirmed(source.confirmed())
                    .setSortOrder(index);
            requireOneRow(
                    compatibilityIssueMapper.insert(entity),
                    "保存兼容性问题失败"
            );
        }
    }

    private void saveSuggestions(
            DiagnosisRun diagnosisRun,
            DiagnosisResult result
    ) {
        for (int index = 0; index < result.suggestions().size(); index++) {
            SuggestedModification source = result.suggestions().get(index);
            ModificationSuggestion entity = new ModificationSuggestion()
                    .setDiagnosisId(diagnosisRun.getId())
                    .setPriority(source.priority().name())
                    .setActionType(source.actionType().name())
                    .setFilePath(source.filePath())
                    .setTitle(source.title())
                    .setDescription(source.description())
                    .setBeforeContent(source.beforeContent())
                    .setAfterContent(source.afterContent())
                    .setVerification(source.verification())
                    .setStatus(SUGGESTION_INITIAL_STATUS)
                    .setSortOrder(index);
            requireOneRow(
                    modificationSuggestionMapper.insert(entity),
                    "保存修改建议失败"
            );
        }
    }

    private void savePlanSteps(
            DiagnosisRun diagnosisRun,
            DiagnosisResult result
    ) {
        for (UpgradePlanStepResult source : result.planSteps()) {
            UpgradePlanStep entity = new UpgradePlanStep()
                    .setDiagnosisId(diagnosisRun.getId())
                    .setSequenceNo(source.sequenceNo())
                    .setPhase(source.phase().name())
                    .setTitle(source.title())
                    .setDescription(source.description())
                    .setPrerequisites(
                            objectMapper.valueToTree(source.prerequisites())
                    )
                    .setVerification(source.verification())
                    .setRollbackAction(source.rollbackAction())
                    .setEstimatedEffort(source.estimatedEffort())
                    .setStatus(PLAN_STEP_INITIAL_STATUS);
            requireOneRow(
                    upgradePlanStepMapper.insert(entity),
                    "保存升级计划步骤失败"
            );
        }
    }

    private void requireOneRow(int affectedRows, String message) {
        if (affectedRows != 1) {
            throw new IllegalStateException(message);
        }
    }
}
