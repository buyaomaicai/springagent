package com.springagent.knowledge.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 关键词检索通道：pg_trgm 三元组相似度（中英文通用）。
 *
 * <p>直接读 vector_store 表，按 similarity(content, ?) 过滤排序，
 * 命中结果携带完整元数据（source_id/source_url 等），
 * 供 evidence 闭环复用。Postgres 默认 tsvector 分词对中文无效，
 * 因此这里用三元组子串匹配。</p>
 */
@Component
@RequiredArgsConstructor
public class KeywordRetrievalStrategy implements RetrievalStrategy {

    private static final String KEYWORD_SQL = """
            SELECT id, content, metadata, similarity(content, ?) AS score
            FROM vector_store
            WHERE similarity(content, ?) >= ?
            ORDER BY score DESC, id
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "KEYWORD";
    }

    @Override
    public SearchResult retrieve(RetrievalRequest request) {
        List<RetrievedEvidence> evidences = jdbcTemplate.query(
                KEYWORD_SQL,
                new Object[]{
                        request.query(),
                        request.query(),
                        request.minScore(),
                        request.topK()
                },
                new KeywordRowMapper(objectMapper)
        );
        return new SearchResult(evidences, !evidences.isEmpty());
    }
}
