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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.springagent.common.exception.GlobalExceptionHandler;
import com.springagent.common.web.RequestIdContext;
import com.springagent.common.web.RequestIdFilter;
import com.springagent.diagnosis.domain.dto.DiagnosisParserDTO;
import com.springagent.diagnosis.domain.dto.request.DiagnosisRequest;
import com.springagent.diagnosis.domain.dto.stream.StreamChunk;
import com.springagent.diagnosis.domain.dto.stream.StreamCompleted;
import com.springagent.diagnosis.domain.dto.stream.StreamError;
import com.springagent.diagnosis.domain.dto.stream.StreamEvent;
import com.springagent.diagnosis.domain.dto.stream.StreamMetadata;
import com.springagent.diagnosis.model.DiagnosisStream;
import com.springagent.diagnosis.service.IDiagnosisService;
import java.time.Duration;
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

    private DiagnosisController controller;

    @BeforeEach
    void setUp() {
        controller = new DiagnosisController(diagnosisService);
        MDC.put(RequestIdContext.MDC_KEY, REQUEST_ID);
    }

    @AfterEach
    void tearDown() {
        MDC.remove(RequestIdContext.MDC_KEY);
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
