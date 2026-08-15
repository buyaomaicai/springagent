package com.springagent.diagnosis.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springagent.common.Constant.MessageStatus;
import com.springagent.common.api.ErrorCode;
import com.springagent.common.exception.BusinessException;
import com.springagent.diagnosis.domain.dto.DiagnosisParserDTO;
import com.springagent.diagnosis.domain.dto.DiagnosisRunDTO;
import com.springagent.diagnosis.domain.dto.request.DiagnosisRequest;
import com.springagent.diagnosis.entity.ChatAttachment;
import com.springagent.diagnosis.entity.ChatConversation;
import com.springagent.diagnosis.entity.ChatMessage;
import com.springagent.diagnosis.entity.DiagnosisRun;
import com.springagent.diagnosis.model.DiagnosisPromptContext;
import com.springagent.diagnosis.model.DiagnosisRunStatus;
import com.springagent.diagnosis.model.DiagnosisStream;
import com.springagent.diagnosis.model.ProjectInput;
import com.springagent.diagnosis.service.IChatConversationService;
import com.springagent.diagnosis.service.IChatMessageService;
import com.springagent.diagnosis.service.IDiagnosisRunService;
import com.springagent.diagnosis.tool.DiagnosisPromptBuilder;
import com.springagent.knowledge.service.KnowledgeRetrievalService;
import com.springagent.parser.ProjectArtifactParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class DiagnosisServiceImplTests {

    private static final UUID CONVERSATION_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000101"
    );

    @Mock
    private DeepSeekChatModel chatModel;

    @Mock
    private IChatConversationService chatConversationService;

    @Mock
    private IChatMessageService chatMessageService;

    @Mock
    private KnowledgeRetrievalService knowledgeRetrievalService;

    @Mock
    private ProjectArtifactParser<ProjectInput> pomXmlParser;

    @Mock
    private DiagnosisPromptBuilder diagnosisPromptBuilder;

    @Mock
    private DiagnosisRunLifecycleService diagnosisRunLifecycleService;

    @Mock
    private IDiagnosisRunService diagnosisRunService;

    private DiagnosisServiceImpl diagnosisService;
    private Prompt prompt;

    @BeforeEach
    void setUp() {
        diagnosisService = new DiagnosisServiceImpl(
                chatModel,
                chatConversationService,
                chatMessageService,
                knowledgeRetrievalService,
                pomXmlParser,
                diagnosisPromptBuilder,
                diagnosisRunLifecycleService,
                new ObjectMapper(),
                diagnosisRunService
        );
        prompt = new Prompt("test prompt");

        lenient().when(chatConversationService.getOrCreateConversation(any()))
                .thenReturn(new ChatConversation().setId(CONVERSATION_ID));
        lenient().when(chatMessageService.gethistory(CONVERSATION_ID))
                .thenReturn(List.of());
        lenient().when(knowledgeRetrievalService.searchSpringBoot30(any()))
                .thenReturn(List.of());
        lenient().when(diagnosisPromptBuilder.build(
                        any(DiagnosisPromptContext.class)
                ))
                .thenReturn(prompt);
    }

    @Test
    void returnsDiagnosisRunWithAssociatedResponse() {
        UUID diagnosisId = UUID.randomUUID();
        UUID responseMessageId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.now().minusMinutes(1);
        OffsetDateTime startedAt = createdAt.plusSeconds(2);
        OffsetDateTime completedAt = startedAt.plusSeconds(10);
        DiagnosisRun run = new DiagnosisRun()
                .setId(diagnosisId)
                .setConversationId(CONVERSATION_ID)
                .setResponseMessageId(responseMessageId)
                .setQuestion("How should I upgrade?")
                .setStatus(DiagnosisRunStatus.SUCCEEDED.name())
                .setModelProvider("DeepSeek")
                .setModelName("deepseek-chat")
                .setPromptVersion("diagnosis-v1")
                .setStartedAt(startedAt)
                .setCompletedAt(completedAt)
                .setCreatedAt(createdAt);
        ChatMessage responseMessage = new ChatMessage()
                .setId(responseMessageId)
                .setContent("Upgrade diagnosis result");
        when(diagnosisRunService.getById(diagnosisId)).thenReturn(run);
        when(chatMessageService.getById(responseMessageId))
                .thenReturn(responseMessage);

        DiagnosisRunDTO result = diagnosisService.getRun(diagnosisId);

        assertEquals(diagnosisId, result.getId());
        assertEquals(CONVERSATION_ID, result.getConversationId());
        assertEquals("How should I upgrade?", result.getQuestion());
        assertEquals(DiagnosisRunStatus.SUCCEEDED.name(), result.getStatus());
        assertEquals("Upgrade diagnosis result", result.getResponse());
        assertEquals(createdAt, result.getCreatedAt());
        assertEquals(startedAt, result.getStartedAt());
        assertEquals(completedAt, result.getCompletedAt());
    }

    @Test
    void rejectsMissingDiagnosisRun() {
        UUID diagnosisId = UUID.randomUUID();
        when(diagnosisRunService.getById(diagnosisId)).thenReturn(null);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> diagnosisService.getRun(diagnosisId)
        );

        assertEquals(ErrorCode.DIAGNOSIS_RUN_NOT_FOUND, error.getErrorCode());
    }

    @Test
    void reportsInternalErrorWhenResponseMessageIsMissing() {
        UUID diagnosisId = UUID.randomUUID();
        UUID responseMessageId = UUID.randomUUID();
        DiagnosisRun run = new DiagnosisRun()
                .setId(diagnosisId)
                .setResponseMessageId(responseMessageId);
        when(diagnosisRunService.getById(diagnosisId)).thenReturn(run);
        when(chatMessageService.getById(responseMessageId)).thenReturn(null);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> diagnosisService.getRun(diagnosisId)
        );

        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, error.getErrorCode());
    }

    @Test
    void createsRunAndMarksItSucceededAfterModelCompletes() {
        when(chatModel.stream(prompt)).thenReturn(Flux.just(
                response("first"),
                response(" second")
        ));

        DiagnosisStream stream = diagnosisService.callDiagnosis(request());

        List<String> chunks = stream.content()
                .collectList()
                .block(Duration.ofSeconds(1));

        assertEquals(List.of("first", " second"), chunks);
        assertEquals(CONVERSATION_ID, stream.conversationId());
        assertNotNull(stream.diagnosisId());

        PreparedObjects prepared = captureCreatedRun();
        assertEquals(DiagnosisRunStatus.QUEUED.name(), prepared.run().getStatus());
        assertTrue(prepared.run().getProjectSnapshot().isObject());
        assertTrue(prepared.run().getProjectSnapshot().isEmpty());
        assertTrue(prepared.run().getTargetSnapshot().isObject());
        assertEquals(prepared.request().getId(), prepared.run().getRequestMessageId());
        assertEquals(prepared.response().getId(), prepared.run().getResponseMessageId());
        assertEquals(MessageStatus.PENDING, prepared.response().getStatus());
        assertNull(prepared.attachment());

        verify(diagnosisRunLifecycleService)
                .markRunning(prepared.run());
        verify(diagnosisRunLifecycleService).markSucceeded(
                eq(prepared.run()),
                eq("first second"),
                any(OffsetDateTime.class)
        );
    }

    @Test
    void recordsPartialContentWhenModelStreamFails() {
        IllegalStateException modelError = new IllegalStateException(
                "upstream unavailable"
        );
        when(chatModel.stream(prompt)).thenReturn(Flux.concat(
                Flux.just(response("partial")),
                Flux.error(modelError)
        ));

        DiagnosisStream stream = diagnosisService.callDiagnosis(request());

        IllegalStateException actualError = assertThrows(
                IllegalStateException.class,
                () -> stream.content().blockLast(Duration.ofSeconds(1))
        );

        assertSame(modelError, actualError);
        PreparedObjects prepared = captureCreatedRun();
        verify(diagnosisRunLifecycleService).markFailed(
                eq(prepared.run()),
                eq("partial"),
                eq("MODEL_STREAM_FAILED"),
                eq("upstream unavailable"),
                any(OffsetDateTime.class)
        );
    }

    @Test
    void marksQueuedRunFailedWhenKnowledgePreparationFails() {
        IllegalStateException retrievalError = new IllegalStateException(
                "vector store unavailable"
        );
        when(knowledgeRetrievalService.searchSpringBoot30(any()))
                .thenThrow(retrievalError);

        IllegalStateException actualError = assertThrows(
                IllegalStateException.class,
                () -> diagnosisService.callDiagnosis(request())
        );

        assertSame(retrievalError, actualError);
        PreparedObjects prepared = captureCreatedRun();
        verify(diagnosisRunLifecycleService).markFailed(
                eq(prepared.run()),
                eq(""),
                eq("DIAGNOSIS_PREPARATION_FAILED"),
                eq("vector store unavailable"),
                any(OffsetDateTime.class)
        );
    }

    @Test
    void schedulesCancelledStateWithoutBlockingCancellingThread() {
        when(chatModel.stream(prompt)).thenReturn(Flux.never());
        DiagnosisStream stream = diagnosisService.callDiagnosis(request());

        Disposable subscription = stream.content().subscribe();
        PreparedObjects prepared = captureCreatedRun();
        verify(diagnosisRunLifecycleService, timeout(1_000))
                .markRunning(prepared.run());

        subscription.dispose();

        verify(diagnosisRunLifecycleService, timeout(1_000))
                .markCancelled(
                        eq(prepared.run()),
                        eq(""),
                        eq("客户端取消了诊断流"),
                        any(OffsetDateTime.class)
                );
    }

    @Test
    void storesPomAttachmentAndImmutableProjectSnapshot() {
        byte[] pom = "<project/>".getBytes(StandardCharsets.UTF_8);
        ProjectInput projectInput = new ProjectInput(
                "com.example",
                "demo",
                "1.0.0",
                "jar",
                "17",
                "3.0.0",
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
        when(pomXmlParser.parse(any(InputStream.class)))
                .thenReturn(projectInput);

        DiagnosisParserDTO request = new DiagnosisParserDTO();
        request.setConversationId(CONVERSATION_ID);
        request.setInput("diagnose project");
        request.setFileName("pom.xml");
        request.setMediaType("application/xml");
        request.setContent(pom);

        diagnosisService.prepare(request);

        PreparedObjects prepared = captureCreatedRun();
        assertNotNull(prepared.attachment());
        assertEquals(prepared.request().getId(), prepared.attachment().getMessageId());
        assertEquals("POM_XML", prepared.attachment().getArtifactType());
        assertEquals("pom.xml", prepared.attachment().getFileName());
        assertEquals("<project/>", prepared.attachment().getContentText());
        assertEquals(
                "demo",
                prepared.run().getProjectSnapshot().get("artifactId").asText()
        );
        assertEquals(
                "3.0.0",
                prepared.run().getProjectSnapshot()
                        .get("springBootVersion")
                        .asText()
        );
    }

    private DiagnosisRequest request() {
        DiagnosisRequest request = new DiagnosisRequest();
        request.setConversationId(CONVERSATION_ID);
        request.setInput("diagnose project");
        return request;
    }

    private ChatResponse response(String text) {
        return new ChatResponse(List.of(
                new Generation(new AssistantMessage(text))
        ));
    }

    private PreparedObjects captureCreatedRun() {
        ArgumentCaptor<ChatMessage> requestCaptor =
                ArgumentCaptor.forClass(ChatMessage.class);
        ArgumentCaptor<ChatMessage> responseCaptor =
                ArgumentCaptor.forClass(ChatMessage.class);
        ArgumentCaptor<ChatAttachment> attachmentCaptor =
                ArgumentCaptor.forClass(ChatAttachment.class);
        ArgumentCaptor<DiagnosisRun> runCaptor =
                ArgumentCaptor.forClass(DiagnosisRun.class);

        verify(diagnosisRunLifecycleService).createRun(
                requestCaptor.capture(),
                responseCaptor.capture(),
                attachmentCaptor.capture(),
                runCaptor.capture()
        );
        return new PreparedObjects(
                requestCaptor.getValue(),
                responseCaptor.getValue(),
                attachmentCaptor.getValue(),
                runCaptor.getValue()
        );
    }

    private record PreparedObjects(
            ChatMessage request,
            ChatMessage response,
            ChatAttachment attachment,
            DiagnosisRun run
    ) {
    }
}
