package com.springagent.knowledge.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class KeywordRetrievalStrategyTests {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void mapsRowsToScoredEvidenceWithMetadata() throws SQLException {
        ResultSet resultSet = org.mockito.Mockito.mock(ResultSet.class);
        when(resultSet.getString("id")).thenReturn("chunk-1");
        when(resultSet.getString("content"))
                .thenReturn("javax to jakarta migration");
        when(resultSet.getString("metadata"))
                .thenReturn("{\"source_id\":\"spring-boot-wiki\"}");
        when(resultSet.getDouble("score")).thenReturn(0.72);

        KeywordRowMapper mapper = new KeywordRowMapper(new ObjectMapper());
        RetrievedEvidence evidence = mapper.mapRow(resultSet, 0);

        assertEquals("chunk-1", evidence.document().getId());
        assertEquals(0.72, evidence.score(), 1e-9);
        assertEquals(RetrievalChannel.KEYWORD, evidence.channel());
        assertEquals(
                "spring-boot-wiki",
                evidence.document().getMetadata().get("source_id")
        );
    }

    @Test
    void issuesSimilarityQueryWithRequestParams() {
        when(jdbcTemplate.query(
                anyString(),
                any(Object[].class),
                any(org.springframework.jdbc.core.RowMapper.class)
        )).thenReturn(java.util.List.of());
        KeywordRetrievalStrategy strategy =
                new KeywordRetrievalStrategy(jdbcTemplate, new ObjectMapper());

        strategy.retrieve(new RetrievalRequest(
                "javax.servlet", 5, 0.3, Map.of()
        ));

        org.mockito.ArgumentCaptor<Object[]> params =
                org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(
                org.mockito.ArgumentMatchers.contains("similarity(content"),
                params.capture(),
                any(org.springframework.jdbc.core.RowMapper.class)
        );
        Object[] captured = params.getValue();
        assertEquals("javax.servlet", captured[0]);
        assertEquals("javax.servlet", captured[1]);
        assertEquals(0.3, captured[2]);
        assertEquals(5, captured[3]);
    }
}
