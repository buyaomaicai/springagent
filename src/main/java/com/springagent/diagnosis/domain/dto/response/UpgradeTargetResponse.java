package com.springagent.diagnosis.domain.dto.response;

/**
 * 诊断建议的目标 Java 和 Spring Boot 版本。
 */
public record UpgradeTargetResponse(
        String javaVersion,
        String springBootVersion
) {
}
