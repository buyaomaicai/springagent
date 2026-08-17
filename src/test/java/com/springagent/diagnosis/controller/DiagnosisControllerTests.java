package com.springagent.diagnosis.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.springagent.common.api.ErrorCode;
import com.springagent.common.api.PageResponse;
import com.springagent.common.exception.BusinessException;
import com.springagent.common.exception.GlobalExceptionHandler;
import com.springagent.common.web.RequestIdContext;
import com.springagent.common.web.RequestIdFilter;
import com.springagent.diagnosis.domain.dto.DiagnosisParserDTO;
import com.springagent.diagnosis.domain.dto.response.DiagnosisResultResponse;
import com.springagent.diagnosis.domain.dto.response.DiagnosisRunResponse;
import com.springagent.diagnosis.domain.dto.response.DiagnosisRunSummaryResponse;
import com.springagent.diagnosis.domain.dto.response.UpgradeTargetResponse;
import com.springagent.diagnosis.domain.dto.request.DiagnosisRequest;
import com.springagent.diagnosis.domain.dto.stream.StreamChunk;
import com.springagent.diagnosis.domain.dto.stream.StreamCompleted;
import com.springagent.diagnosis.domain.dto.stream.StreamError;
import com.springagent.diagnosis.domain.dto.stream.StreamEvent;
import com.springagent.diagnosis.domain.dto.stream.StreamMetadata;
import com.springagent.diagnosis.model.DiagnosisStream;
import com.springagent.diagnosis.model.DiagnosisRunStatus;
import com.springagent.diagnosis.service.IDiagnosisResultQueryService;
import com.springagent.diagnosis.service.IDiagnosisService;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class DiagnosisControllerTests {

    private static final String REQUEST_ID = "stream-test-request";
    private static final UUID CONVERSATION_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000101"
    );
    private static final UUID DIAGNOSIS_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000201"
    );

    @Mock
    private IDiagnosisService diagnosisService;

    @Mock
    private IDiagnosisResultQueryService diagnosisResultQueryService;

    private DiagnosisController controller;

    @BeforeEach
    void setUp() {
        controller = new DiagnosisController(
                diagnosisService,
                diagnosisResultQueryService
        );
        MDC.put(RequestIdContext.MDC_KEY, REQUEST_ID);
    }

    @AfterEach
    void tearDown() {
        MDC.remove(RequestIdContext.MDC_KEY);
    }

    @Test
    void returnsPagedDiagnosisHistory() throws Exception {
        OffsetDateTime createdAt = OffsetDateTime.parse(
                "2026-08-16T12:00:00Z"
        );
        DiagnosisRunSummaryResponse summary =
                new DiagnosisRunSummaryResponse(
                        DIAGNOSIS_ID,
                        CONVERSATION_ID,
                        "Upgrade Spring Boot",
                        DiagnosisRunStatus.SUCCEEDED,
                        "Upgrade is feasible",
                        new UpgradeTargetResponse("21", "4.0"),
                        null,
                        createdAt.plusSeconds(1),
                        createdAt.plusSeconds(10),
                        createdAt
                );
        PageResponse<DiagnosisRunSummaryResponse> page =
                new PageResponse<>(List.of(summary), 1, 1, 20, 1);
        when(diagnosisResultQueryService.listRuns(
                CONVERSATION_ID,
                1,
                20,
                null
        )).thenReturn(page);

        mockMvc().perform(get("/diagnosis/runs")
                        .param("conversationId", CONVERSATION_ID.toString())
                        .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.items[0].diagnosisId")
                        .value(DIAGNOSIS_ID.toString()))
                .andExpect(jsonPath("$.data.items[0].status")
                        .value("SUCCEEDED"))
                .andExpect(jsonPath(
                        "$.data.items[0].target.javaVersion"
                ).value("21"));
    }

    @Test
    void forwardsHistoryStatusFilter() throws Exception {
        when(diagnosisResultQueryService.listRuns(
                CONVERSATION_ID,
                2,
                10,
                DiagnosisRunStatus.FAILED
        )).thenReturn(new PageResponse<>(List.of(), 0, 2, 10, 0));

        mockMvc().perform(get("/diagnosis/runs")
                        .param("conversationId", CONVERSATION_ID.toString())
                        .param("page", "2")
                        .param("size", "10")
                        .param("status", "FAILED")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());

        verify(diagnosisResultQueryService).listRuns(
                CONVERSATION_ID,
                2,
                10,
                DiagnosisRunStatus.FAILED
        );
    }

    @Test
    void rejectsInvalidHistoryPagination() throws Exception {
        mockMvc().perform(get("/diagnosis/runs")
                        .param("conversationId", CONVERSATION_ID.toString())
                        .param("size", "101")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    void rejectsHistoryQueryWithoutConversationId() throws Exception {
        mockMvc().perform(get("/diagnosis/runs")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field")
                        .value("conversationId"));
    }

    @Test
    void returnsDiagnosisRunById() throws Exception {
        DiagnosisRunResponse diagnosisRun = new DiagnosisRunResponse();
        diagnosisRun.setId(DIAGNOSIS_ID);
        diagnosisRun.setConversationId(CONVERSATION_ID);
        diagnosisRun.setStatus("SUCCEEDED");
        diagnosisRun.setResponse("Upgrade diagnosis result");
        when(diagnosisService.getRun(DIAGNOSIS_ID))
                .thenReturn(diagnosisRun);

        mockMvc().perform(get("/diagnosis/runs/{diagnosisId}", DIAGNOSIS_ID)
                        .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID))
                .andExpect(jsonPath("$.data.id").value(DIAGNOSIS_ID.toString()))
                .andExpect(jsonPath("$.data.conversationId")
                        .value(CONVERSATION_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.response")
                        .value("Upgrade diagnosis result"));
    }

    @Test
    void returnsNotFoundWhenDiagnosisRunDoesNotExist() throws Exception {
        when(diagnosisService.getRun(DIAGNOSIS_ID)).thenThrow(
                new BusinessException(ErrorCode.DIAGNOSIS_RUN_NOT_FOUND)
        );

        mockMvc().perform(get("/diagnosis/runs/{diagnosisId}", DIAGNOSIS_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("DIAGNOSIS_RUN_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("诊断运行不存在"));
    }

    @Test
    void rejectsInvalidDiagnosisId() throws Exception {
        mockMvc().perform(get("/diagnosis/runs/{diagnosisId}", "not-a-uuid")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void returnsStructuredDiagnosisResult() throws Exception {
        DiagnosisResultResponse result = new DiagnosisResultResponse(
                DIAGNOSIS_ID,
                "Upgrade is feasible",
                new UpgradeTargetResponse("17", "3.2.0"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
        when(diagnosisResultQueryService.getResult(DIAGNOSIS_ID))
                .thenReturn(result);

        mockMvc().perform(get(
                        "/diagnosis/runs/{diagnosisId}/result",
                        DIAGNOSIS_ID
                ).header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID))
                .andExpect(jsonPath("$.data.diagnosisId")
                        .value(DIAGNOSIS_ID.toString()))
                .andExpect(jsonPath("$.data.summary")
                        .value("Upgrade is feasible"))
                .andExpect(jsonPath("$.data.target.javaVersion")
                        .value("17"))
                .andExpect(jsonPath("$.data.target.springBootVersion")
                        .value("3.2.0"))
                .andExpect(jsonPath("$.data.risks").isArray())
                .andExpect(jsonPath("$.data.planSteps").isArray())
                .andExpect(jsonPath("$.data.evidence").isArray());
    }

    @Test
    void returnsConflictWhenStructuredResultIsNotReady() throws Exception {
        when(diagnosisResultQueryService.getResult(DIAGNOSIS_ID))
                .thenThrow(new BusinessException(
                        ErrorCode.CONFLICT,
                        "诊断尚未完成"
                ));

        mockMvc().perform(get(
                        "/diagnosis/runs/{diagnosisId}/result",
                        DIAGNOSIS_ID
                ).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("诊断尚未完成"));
    }

    @Test
    void emitsMetadataChunksAndCompletionInOrder() {
        when(diagnosisService.callDiagnosis(any()))
                .thenReturn(diagnosisStream(Flux.just("first", "second")));

        List<ServerSentEvent<Object>> events = controller
                .callDiagnosis(diagnosisRequest())
                .collectList()
                .block(Duration.ofSeconds(1));

        assertNotNull(events);
        assertEquals(List.of("meta", "chunk", "chunk", "done"),
                events.stream().map(ServerSentEvent::event).toList());

        assertEvent(events.get(0), 0, StreamMetadata.class);
        assertEvent(events.get(1), 1, StreamChunk.class);
        assertEvent(events.get(2), 2, StreamChunk.class);
        assertEvent(events.get(3), 3, StreamCompleted.class);

        StreamEvent<?> metadataEvent = (StreamEvent<?>) events.get(0).data();
        StreamMetadata metadata = (StreamMetadata) metadataEvent.data();
        assertEquals(CONVERSATION_ID, metadata.conversationId());
        assertEquals(DIAGNOSIS_ID, metadata.diagnosisId());

        StreamEvent<?> chunkEvent = (StreamEvent<?>) events.get(1).data();
        assertEquals("first", ((StreamChunk) chunkEvent.data()).content());
    }

    @Test
    void emitsSafeStructuredErrorWithoutDoneEvent() {
        when(diagnosisService.callDiagnosis(any()))
                .thenReturn(diagnosisStream(Flux.error(
                        new IllegalStateException("secret detail")
                )));

        List<ServerSentEvent<Object>> events = controller
                .callDiagnosis(diagnosisRequest())
                .collectList()
                .block(Duration.ofSeconds(1));

        assertNotNull(events);
        assertEquals(List.of("meta", "error"),
                events.stream().map(ServerSentEvent::event).toList());

        StreamEvent<?> errorEvent = (StreamEvent<?>) events.get(1).data();
        StreamError streamError = assertInstanceOf(
                StreamError.class,
                errorEvent.data()
        );
        assertEquals("DIAGNOSIS_STREAM_FAILED", streamError.code());
        assertEquals("诊断生成失败", streamError.message());
        assertFalse(streamError.message().contains("secret detail"));
        assertTrue(streamError.retryable());
    }

    @Test
    void serializesEventsAsSseWithJsonData() throws Exception {
        when(diagnosisService.callDiagnosis(any()))
                .thenReturn(diagnosisStream(Flux.just("hello")));
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();

        MvcResult result = mockMvc.perform(post("/diagnosis/stream")
                        .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": "00000000-0000-0000-0000-000000000101",
                                  "input": "How should I upgrade?"
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIdFilter.HEADER_NAME, REQUEST_ID))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.TEXT_EVENT_STREAM
                ))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("event:meta")
                ))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("event:chunk")
                ))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString(
                                "\"diagnosisId\":\"" + DIAGNOSIS_ID + "\""
                        )
                ))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("\"timestamp\":\"")
                ))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString(
                                "\"content\":\"hello\""
                        )
                ))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("event:done")
                ));
    }

    @Test
    void bindsMultipartPomAndStreamsPreparedDiagnosis() throws Exception {
        when(diagnosisService.prepare(any()))
                .thenReturn(diagnosisStream(Flux.just("project diagnosis")));
        MockMvc mockMvc = mockMvc();
        String pom = """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <artifactId>upload-demo</artifactId>
                </project>
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "pom.xml",
                MediaType.APPLICATION_XML_VALUE,
                pom.getBytes(StandardCharsets.UTF_8)
        );

        MvcResult result = mockMvc.perform(
                        multipart("/diagnosis/prepare")
                                .file(file)
                                .param(
                                        "conversationId",
                                        "00000000-0000-0000-0000-000000000101"
                                )
                                .param("input", "请分析这个项目")
                                .header(
                                        RequestIdFilter.HEADER_NAME,
                                        REQUEST_ID
                                )
                                .accept(MediaType.TEXT_EVENT_STREAM)
                )
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.TEXT_EVENT_STREAM
                ))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("event:meta")
                ))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("event:chunk")
                ))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString(
                                "\"content\":\"project diagnosis\""
                        )
                ))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("event:done")
                ));

        ArgumentCaptor<DiagnosisParserDTO> captor =
                ArgumentCaptor.forClass(DiagnosisParserDTO.class);
        verify(diagnosisService).prepare(captor.capture());

        DiagnosisParserDTO captured = captor.getValue();
        assertEquals(
                UUID.fromString(
                        "00000000-0000-0000-0000-000000000101"
                ),
                captured.getConversationId()
        );
        assertEquals("请分析这个项目", captured.getInput());
        assertEquals("pom.xml", captured.getFileName());
        assertEquals(MediaType.APPLICATION_XML_VALUE, captured.getMediaType());
        assertEquals(pom, new String(
                captured.getContent(),
                StandardCharsets.UTF_8
        ));
    }

    @Test
    void rejectsPrepareRequestWithoutPomFile() throws Exception {
        MockMvc mockMvc = mockMvc();

        mockMvc.perform(multipart("/diagnosis/prepare")
                        .param("input", "请分析这个项目"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ));
    }

    @Test
    void rejectsPrepareRequestWithEmptyPomFile() throws Exception {
        MockMvc mockMvc = mockMvc();
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "pom.xml",
                MediaType.APPLICATION_XML_VALUE,
                new byte[0]
        );

        mockMvc.perform(multipart("/diagnosis/prepare")
                        .file(emptyFile)
                        .param("input", "请分析这个项目"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ));
    }

    private DiagnosisRequest diagnosisRequest() {
        DiagnosisRequest request = new DiagnosisRequest();
        request.setConversationId(UUID.fromString(
                CONVERSATION_ID.toString()
        ));
        request.setInput("How should I upgrade?");
        return request;
    }

    private DiagnosisStream diagnosisStream(Flux<String> content) {
        return new DiagnosisStream(DIAGNOSIS_ID, CONVERSATION_ID, content);
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    private void assertEvent(
            ServerSentEvent<Object> serverSentEvent,
            long expectedSequence,
            Class<?> expectedDataType
    ) {
        StreamEvent<?> event = assertInstanceOf(
                StreamEvent.class,
                serverSentEvent.data()
        );
        assertEquals(REQUEST_ID, event.requestId());
        assertEquals(expectedSequence, event.sequence());
        assertInstanceOf(expectedDataType, event.data());
        assertNotNull(event.timestamp());
    }
}
