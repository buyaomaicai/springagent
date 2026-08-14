package com.springagent.parser.exception;

import com.springagent.parser.ArtifactType;
import java.util.Objects;

/**
 * 项目材料无法被安全、完整解析时抛出的异常。
 */
public class ProjectArtifactParseException extends RuntimeException {

    private final ArtifactType artifactType;
    private final Reason reason;

    public ProjectArtifactParseException(
            ArtifactType artifactType,
            Reason reason,
            String message
    ) {
        this(artifactType, reason, message, null);
    }

    public ProjectArtifactParseException(
            ArtifactType artifactType,
            Reason reason,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.artifactType = Objects.requireNonNull(
                artifactType,
                "artifactType must not be null"
        );
        this.reason = Objects.requireNonNull(
                reason,
                "reason must not be null"
        );
    }

    public ArtifactType getArtifactType() {
        return artifactType;
    }

    public Reason getReason() {
        return reason;
    }

    /**
     * 解析失败的稳定分类，可用于日志、测试和 API 错误映射。
     */
    public enum Reason {

        /**
         * 输入不存在或者内容为空。
         */
        EMPTY_CONTENT,

        /**
         * 输入内容超过允许的最大大小。
         */
        CONTENT_TOO_LARGE,

        /**
         * XML、日志或依赖列表格式错误。
         */
        MALFORMED_CONTENT,

        /**
         * 输入包含 DOCTYPE、外部实体等不安全内容。
         */
        UNSAFE_CONTENT,

        /**
         * 输入格式或模型版本暂不支持。
         */
        UNSUPPORTED_FORMAT,

        /**
         * 缺少 artifactId 等必要字段。
         */
        MISSING_REQUIRED_FIELD,

        /**
         * 读取输入流时发生异常。
         */
        IO_ERROR
    }
}