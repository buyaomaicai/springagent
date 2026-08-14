package com.springagent.diagnosis.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.springagent.common.persistence.JsonbTypeHandler;
import com.springagent.common.persistence.UuidTypeHandler;
import com.springagent.diagnosis.entity.ChatAttachment;
import com.springagent.diagnosis.entity.ChatMessage;
import com.springagent.diagnosis.entity.DiagnosisRun;
import com.springagent.diagnosis.mapper.ChatAttachmentMapper;
import com.springagent.diagnosis.mapper.ChatMessageMapper;
import com.springagent.diagnosis.mapper.DiagnosisRunMapper;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiagnosisRunLifecycleServiceTests {

    @BeforeAll
    static void initializeMyBatisTableMetadata() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.getTypeHandlerRegistry().register(
                UUID.class,
                UuidTypeHandler.class
        );
        configuration.getTypeHandlerRegistry().register(
                JsonNode.class,
                JsonbTypeHandler.class
        );
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                configuration,
                "diagnosis-run-lifecycle-tests"
        );
        TableInfoHelper.initTableInfo(assistant, DiagnosisRun.class);
        TableInfoHelper.initTableInfo(assistant, ChatMessage.class);
    }

    @Mock
    private ChatMessageMapper chatMessageMapper;

    @Mock
    private ChatAttachmentMapper chatAttachmentMapper;

    @Mock
    private DiagnosisRunMapper diagnosisRunMapper;

    private DiagnosisRunLifecycleService lifecycleService;

    @BeforeEach
    void setUp() {
        lifecycleService = new DiagnosisRunLifecycleService(
                chatMessageMapper,
                chatAttachmentMapper,
                diagnosisRunMapper
        );
    }

    @Test
    void createsMessagesAttachmentAndRunInForeignKeyOrder() {
        ChatMessage request = new ChatMessage();
        ChatMessage response = new ChatMessage();
        ChatAttachment attachment = new ChatAttachment();
        DiagnosisRun run = run(response.getId());
        when(chatMessageMapper.insert(any(ChatMessage.class))).thenReturn(1);
        when(chatAttachmentMapper.insert(attachment)).thenReturn(1);
        when(diagnosisRunMapper.insert(run)).thenReturn(1);

        lifecycleService.createRun(request, response, attachment, run);

        InOrder order = inOrder(
                chatMessageMapper,
                chatAttachmentMapper,
                diagnosisRunMapper
        );
        order.verify(chatMessageMapper).insert(request);
        order.verify(chatMessageMapper).insert(response);
        order.verify(chatAttachmentMapper).insert(attachment);
        order.verify(diagnosisRunMapper).insert(run);
    }

    @Test
    void createsRunWithoutAttachmentForTextOnlyDiagnosis() {
        ChatMessage request = new ChatMessage();
        ChatMessage response = new ChatMessage();
        DiagnosisRun run = run(response.getId());
        when(chatMessageMapper.insert(any(ChatMessage.class))).thenReturn(1);
        when(diagnosisRunMapper.insert(run)).thenReturn(1);

        lifecycleService.createRun(request, response, null, run);

        verify(chatAttachmentMapper, never())
                .insert(any(ChatAttachment.class));
        verify(diagnosisRunMapper).insert(run);
    }

    @Test
    void transitionsRunAndResponseMessageTogether() {
        DiagnosisRun run = run(UUID.randomUUID());
        run.setStartedAt(OffsetDateTime.now());
        when(diagnosisRunMapper.update(isNull(), any())).thenReturn(1);
        when(chatMessageMapper.update(isNull(), any())).thenReturn(1);

        lifecycleService.markRunning(run);
        lifecycleService.markSucceeded(
                run,
                "complete result",
                OffsetDateTime.now()
        );

        verify(diagnosisRunMapper, org.mockito.Mockito.times(2))
                .update(isNull(), any());
        verify(chatMessageMapper, org.mockito.Mockito.times(2))
                .update(isNull(), any());
    }

    @Test
    void rejectsTransitionWhenRunIsAlreadyTerminal() {
        DiagnosisRun run = run(UUID.randomUUID());
        when(diagnosisRunMapper.update(isNull(), any())).thenReturn(0);

        assertThrows(
                IllegalStateException.class,
                () -> lifecycleService.markCancelled(
                        run,
                        "partial",
                        "cancelled",
                        OffsetDateTime.now()
                )
        );

        verify(chatMessageMapper, never()).update(isNull(), any());
    }

    private DiagnosisRun run(UUID responseMessageId) {
        return new DiagnosisRun()
                .setId(UUID.randomUUID())
                .setResponseMessageId(responseMessageId)
                .setStatus("QUEUED");
    }
}
