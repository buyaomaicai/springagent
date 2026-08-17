package com.springagent.knowledge.chunking;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 分块配置（application.yml 的 knowledge.chunking.*）。
 *
 * <p>overlap 必须小于 chunk-size，否则滑动窗口会倒退造成死循环，
 * 策略实现会对它做钳制。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "knowledge.chunking")
public class KnowledgeChunkingProperties {

    private String strategy = "HEADING_AWARE";

    private int chunkSize = 500;

    private int overlap = 50;
}
