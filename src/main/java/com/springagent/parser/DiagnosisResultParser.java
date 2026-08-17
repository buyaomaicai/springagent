package com.springagent.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springagent.common.exception.DiagnosisResultParseException;
import com.springagent.diagnosis.domain.dto.result.DiagnosisResult;
import jakarta.validation.ConstraintViolation;
import org.springframework.stereotype.Component;
import jakarta.validation.Validator;

import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DiagnosisResultParser {

    private final ObjectMapper objectMapper;
    private final Validator validator;

    public DiagnosisResultParser(
            ObjectMapper objectMapper,
            Validator validator
    ) {
        // copy() 避免修改 Spring 全局 ObjectMapper。
        this.objectMapper = objectMapper.copy()
                // 模型多返回字段时忽略，保证向前兼容。
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                // 拒绝 "{} other text" 这样的尾随内容。
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                // 拒绝用数字表示枚举，例如 severity: 1。
                .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS);
        this.validator = validator;
    }

    public DiagnosisResult parse(String modelOutput) {
        if (modelOutput == null || modelOutput.isBlank()) {
            throw new DiagnosisResultParseException(
                    "模型没有返回诊断结果"
            );
        }

        String json = unwrapJsonFence(modelOutput);

        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new DiagnosisResultParseException(
                    "模型返回的内容不是合法 JSON",
                    exception
            );
        }

        if (root == null || !root.isObject()) {
            throw new DiagnosisResultParseException(
                    "诊断结果根节点必须是 JSON 对象"
            );
        }

        DiagnosisResult result;
        try {
            result = objectMapper.treeToValue(
                    root,
                    DiagnosisResult.class
            );
        } catch (JsonProcessingException exception) {
            // 非法枚举、字段类型错误等会进入这里。
            throw new DiagnosisResultParseException(
                    "诊断结果字段类型或枚举值不合法",
                    exception
            );
        }

        validate(result);
        return result;
    }

    /**
     * 只剥离完整包裹 JSON 的 Markdown 代码块。
     * 不尝试从任意文本中猜测或截取 JSON。
     */
    private String unwrapJsonFence(String modelOutput) {
        String text = modelOutput.strip();

        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1).stripLeading();
        }

        if (!text.startsWith("```")) {
            return text;
        }

        int openingLineEnd = text.indexOf('\n');
        if (openingLineEnd < 0) {
            throw new DiagnosisResultParseException(
                    "Markdown JSON 代码块缺少正文"
            );
        }

        String language = text.substring(3, openingLineEnd).trim();
        if (!language.isEmpty() && !language.equalsIgnoreCase("json")) {
            throw new DiagnosisResultParseException(
                    "模型返回了不支持的代码块类型: " + language
            );
        }

        int closingFenceStart = text.lastIndexOf("```");
        if (closingFenceStart <= openingLineEnd) {
            throw new DiagnosisResultParseException(
                    "Markdown JSON 代码块没有正确结束"
            );
        }

        if (!text.substring(closingFenceStart + 3).isBlank()) {
            throw new DiagnosisResultParseException(
                    "JSON 代码块结束后存在额外内容"
            );
        }

        String json = text.substring(
                openingLineEnd + 1,
                closingFenceStart
        ).strip();

        if (json.isEmpty()) {
            throw new DiagnosisResultParseException(
                    "Markdown JSON 代码块内容为空"
            );
        }

        return json;
    }

    private void validate(DiagnosisResult result) {
        Set<ConstraintViolation<DiagnosisResult>> violations =
                validator.validate(result);

        if (violations.isEmpty()) {
            return;
        }

        String details = violations.stream()
                .sorted(Comparator.comparing(
                        violation -> violation.getPropertyPath().toString()
                ))
                .map(violation -> violation.getPropertyPath()
                        + ": " + violation.getMessage())
                .collect(Collectors.joining("; "));

        throw new DiagnosisResultParseException(
                "诊断结果缺少必要字段或字段不合法: " + details
        );
    }
}