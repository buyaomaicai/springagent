package com.springagent.diagnosis.service;

import com.springagent.diagnosis.domain.dto.result.DiagnosisResult;
import com.springagent.diagnosis.entity.DiagnosisRun;
import java.time.OffsetDateTime;

/**
 * 将已经校验通过的模型诊断结果保存为可查询的业务数据。
 */
public interface IDiagnosisResultPersistenceService {

    /**
     * 原子保存结构化结果、模型原文和本次诊断的成功状态。
     *
     * @param diagnosisRun 当前诊断运行
     * @param result 已经完成 JSON 解析和 Bean Validation 校验的结果
     * @param modelOutput 模型返回的完整原始文本
     * @param completedAt 诊断完成时间
     */
    void save(
            DiagnosisRun diagnosisRun,
            DiagnosisResult result,
            String modelOutput,
            OffsetDateTime completedAt
    );
}
