package com.springagent.knowledge.retrieval;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 在 Spring AI 创建 vector_store 表之后，为其补充关键词检索能力：
 * pg_trgm 扩展 + content 列的 GIN 三元组索引。
 *
 * <p>为什么不能放 docker init 脚本：vector_store 表由 Spring AI 在应用
 * 启动时创建（initialize-schema），init 脚本执行时表还不存在。
 * 本组件注入 {@link VectorStore} 强制依赖，Spring 保证 VectorStore
 * 完整初始化（含 afterPropertiesSet 建表）后才执行本组件的初始化。</p>
 *
 * <p>为什么用 pg_trgm 而不是 tsvector：Postgres 默认分词按空格切分，
 * 对中文（无空格）无效；pg_trgm 三元组匹配子串，中英文通用。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorStoreSchemaEnhancer {

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void enhance() {
        try {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
            jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS vector_store_content_trgm_idx
                    ON vector_store USING GIN (content gin_trgm_ops)
                    """);
            log.info("vector_store 表已增强 pg_trgm 关键词索引");
        } catch (RuntimeException exception) {
            // 表缺失或扩展不可用时记录告警，不阻断应用启动；
            // 关键词通道会在真正查询时报错，便于定位。
            log.warn("vector_store 表增强失败: {}", exception.getMessage());
        }
    }
}
