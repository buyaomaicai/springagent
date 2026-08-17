package com.springagent.diagnosis.service;

import com.springagent.diagnosis.domain.dto.result.DiagnosisResult;
import java.util.List;
import org.springframework.ai.document.Document;

public interface IDiagnosisResultStructuringService {

    /**
     * 把模型原始输出解析并校正为结构化结果。
     *
     * @param diagnosisContent 模型输出的原始文本
     * @param retrievedDocuments 本次检索到的知识文档，与提示词中的 [REF-i] 编号一一对应，
     *                           用于校正 evidence 引用（服务端权威来源）
     */
    DiagnosisResult structure(
            String diagnosisContent,
            List<Document> retrievedDocuments
    );
}
