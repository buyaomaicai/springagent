package com.springagent.knowledge.retrieval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.RowMapper;

/**
 * 把 vector_store 行映射为带分数的检索结果（关键词通道专用）。
 */
@RequiredArgsConstructor
class KeywordRowMapper implements RowMapper<RetrievedEvidence> {

    private static final TypeReference<Map<String, Object>> METADATA_TYPE =
            new TypeReference<>() {
            };

    private final ObjectMapper objectMapper;

    @Override
    public RetrievedEvidence mapRow(ResultSet resultSet, int rowNum)
            throws SQLException {
        Document document = Document.builder()
                .id(resultSet.getString("id"))
                .text(resultSet.getString("content"))
                .metadata(parseMetadata(resultSet.getString("metadata")))
                .score(resultSet.getDouble("score"))
                .build();
        return new RetrievedEvidence(
                document,
                resultSet.getDouble("score"),
                RetrievalChannel.KEYWORD
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metadataJson, METADATA_TYPE);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "读取知识 chunk 元数据失败",
                    exception
            );
        }
    }
}
