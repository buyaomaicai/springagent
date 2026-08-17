package com.springagent.diagnosis.service.impl;

import com.springagent.diagnosis.domain.dto.result.DiagnosisResult;
import com.springagent.diagnosis.domain.dto.result.EvidenceReference;
import com.springagent.diagnosis.service.IDiagnosisResultStructuringService;
import com.springagent.parser.DiagnosisResultParser;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

/**
 * 把模型原始输出解析为结构化结果，并用服务端检索到的真实文档校正 evidence。
 *
 * <p>模型不可信的地方在于可能编造引用来源，因此 evidence 的事实字段（URL、
 * 标题、来源类型、适用版本、置信度、摘录）一律取自检索返回的 {@link Document}；
 * 模型只通过 refIndex 声明"使用了哪几条引用"。refIndex 越界的条目直接丢弃，
 * 保证入库的证据都对应真实存在的检索结果。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiagnosisResultStructuringService
        implements IDiagnosisResultStructuringService {

    private static final int MAX_TITLE_LENGTH = 120;
    private static final int MAX_EXCERPT_LENGTH = 300;

    private final DiagnosisResultParser diagnosisResultParser;

    @Override
    public DiagnosisResult structure(
            String diagnosisContent,
            List<Document> retrievedDocuments
    ) {
        DiagnosisResult parsed = diagnosisResultParser.parse(diagnosisContent);
        return resolveEvidence(parsed, retrievedDocuments);
    }

    /**
     * 逐条校正模型声明的证据引用：refIndex 越界或文档缺少真实来源 URL 的
     * 条目被丢弃，其余条目用服务端文档元数据覆盖权威字段。
     */
    private DiagnosisResult resolveEvidence(
            DiagnosisResult parsed,
            List<Document> documents
    ) {
        if (parsed.evidence().isEmpty()) {
            return parsed;
        }

        List<EvidenceReference> resolved = new ArrayList<>();
        for (EvidenceReference declared : parsed.evidence()) {
            int refIndex = declared.refIndex();
            if (refIndex < 0 || refIndex >= documents.size()) {
                log.warn(
                        "Dropping evidence with out-of-range refIndex={}, "
                                + "retrieved={}",
                        refIndex,
                        documents.size()
                );
                continue;
            }
            Document document = documents.get(refIndex);
            String url = referenceUrl(document);
            if (url.isBlank()) {
                log.warn(
                        "Dropping evidence refIndex={} because the retrieved "
                                + "document has no source_url",
                        refIndex
                );
                continue;
            }
            resolved.add(new EvidenceReference(
                    refIndex,
                    url,
                    referenceTitle(document),
                    metadataText(document, "source_type"),
                    metadataText(document, "component"),
                    metadataText(document, "target_version"),
                    scoreOf(document),
                    excerptOf(document.getText())
            ));
        }
        return new DiagnosisResult(
                parsed.summary(),
                parsed.target(),
                parsed.risks(),
                parsed.compatibilityIssues(),
                parsed.suggestions(),
                parsed.planSteps(),
                resolved
        );
    }

    private String referenceTitle(Document document) {
        String firstLine = document.getText().lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .findFirst()
                .orElse("Unnamed reference");
        return firstLine.length() <= MAX_TITLE_LENGTH
                ? firstLine
                : firstLine.substring(0, MAX_TITLE_LENGTH) + "...";
    }

    private String referenceUrl(Document document) {
        return metadataText(document, "source_url");
    }

    private String metadataText(Document document, String key) {
        Object value = document.getMetadata().get(key);
        return value == null ? "" : value.toString();
    }

    private BigDecimal scoreOf(Document document) {
        Double score = document.getScore();
        return score == null ? null : BigDecimal.valueOf(score);
    }

    private String excerptOf(String text) {
        if (text == null) {
            return null;
        }
        String flat = text.strip();
        return flat.length() <= MAX_EXCERPT_LENGTH
                ? flat
                : flat.substring(0, MAX_EXCERPT_LENGTH) + "...";
    }
}
