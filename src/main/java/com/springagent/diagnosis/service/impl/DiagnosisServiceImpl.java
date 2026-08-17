package com.springagent.diagnosis.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.springagent.common.Constant.MessageStatus;
import com.springagent.common.Constant.SenderRole;
import com.springagent.common.api.ErrorCode;
import com.springagent.common.exception.BusinessException;
import com.springagent.common.exception.DiagnosisResultParseException;
import com.springagent.common.exception.DiagnosisResultPersistenceException;
import com.springagent.diagnosis.domain.dto.DiagnosisParserDTO;
import com.springagent.diagnosis.domain.dto.request.DiagnosisRequest;
import com.springagent.diagnosis.domain.dto.response.DiagnosisRunResponse;
import com.springagent.diagnosis.domain.dto.result.DiagnosisResult;
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
import com.springagent.diagnosis.service.IDiagnosisResultPersistenceService;
import com.springagent.diagnosis.service.IDiagnosisResultStructuringService;
import com.springagent.diagnosis.service.IDiagnosisRunService;
import com.springagent.diagnosis.service.IDiagnosisService;
import com.springagent.diagnosis.tool.DiagnosisPromptBuilder;
import com.springagent.knowledge.retrieval.RetrievedEvidence;
import com.springagent.knowledge.retrieval.RetrievalRequest;
import com.springagent.knowledge.service.KnowledgeRetrievalService;
import com.springagent.parser.ArtifactType;
import com.springagent.parser.ProjectArtifactParser;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 诊断服务实现，负责准备上下文、记录运行快照并流式调用大模型。
 *
 * <p>每次请求都会创建一条 {@link DiagnosisRun} 主记录。用户消息、助手占位消息、
 * 上传附件与运行主记录在同一个短事务中创建；模型流真正被订阅时切换为 RUNNING，
 * 最后根据完成、异常或取消信号进入对应终态。这使每次模型调用都有稳定的
 * diagnosisId，可以串起输入、输出、错误和时间信息。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiagnosisServiceImpl implements IDiagnosisService {

    private static final String MODEL_PROVIDER = "DeepSeek";
    private static final String PROMPT_VERSION = "diagnosis-v2";
    private static final int RETRIEVAL_TOP_K = 5;
    private static final double RETRIEVAL_MIN_SCORE = 0.0;
    private static final String PREPARATION_ERROR_CODE =
            "DIAGNOSIS_PREPARATION_FAILED";
    private static final String STREAM_ERROR_CODE = "MODEL_STREAM_FAILED";
    private static final String RESULT_PARSE_ERROR_CODE =
            "DIAGNOSIS_RESULT_PARSE_FAILED";
    private static final String RESULT_PERSIST_ERROR_CODE =
            "DIAGNOSIS_RESULT_PERSIST_FAILED";
    private static final String CANCEL_DETAIL = "客户端取消了诊断流";

    private final IDiagnosisResultStructuringService
            diagnosisResultStructuringService;
    private final IDiagnosisResultPersistenceService
            diagnosisResultPersistenceService;
    private final DeepSeekChatModel chatModel;
    private final IChatConversationService chatConversationService;
    private final IChatMessageService chatMessageService;
    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final ProjectArtifactParser<ProjectInput> pomXmlParser;
    private final DiagnosisPromptBuilder diagnosisPromptBuilder;
    private final DiagnosisRunLifecycleService diagnosisRunLifecycleService;
    private final ObjectMapper objectMapper;
    private final IDiagnosisRunService diagnosisRunService;

    private record PreparedDiagnosis(
            Prompt prompt,
            DiagnosisRun diagnosisRun,
            List<Document> documents
    ) {
    }

    /**
     * 不带项目文件发起诊断。项目快照会保存为空 JSON 对象。
     */
    @Override
    public DiagnosisStream callDiagnosis(DiagnosisRequest request) {
        ChatConversation conversation = chatConversationService
                .getOrCreateConversation(request.getConversationId());
        PreparedDiagnosis prepared = prepareRun(
                request.getInput(),
                conversation,
                null,
                null
        );
        return toDiagnosisStream(conversation, prepared);
    }

    /**
     * 解析上传的 pom.xml，将原文件和解析后的项目快照一起保存，再发起诊断。
     */
    @Override
    public DiagnosisStream prepare(DiagnosisParserDTO request) {
        ChatConversation conversation = chatConversationService
                .getOrCreateConversation(request.getConversationId());
        byte[] content = request.getContent();
        ProjectInput projectInput = pomXmlParser.parse(
                new ByteArrayInputStream(content)
        );
        ChatAttachment attachment = new ChatAttachment()
                .setId(UUID.randomUUID())
                .setArtifactType(ArtifactType.POM_XML.name())
                .setFileName(request.getFileName())
                .setMediaType(request.getMediaType())
                .setFileSizeBytes((long) content.length)
                .setExtractionStatus(MessageStatus.COMPLETED.name())
                .setContentText(new String(content, StandardCharsets.UTF_8))
                .setParsedContent(objectMapper.valueToTree(projectInput));

        PreparedDiagnosis prepared = prepareRun(
                request.getInput(),
                conversation,
                projectInput,
                attachment
        );
        return toDiagnosisStream(conversation, prepared);
    }

    @Override
    public DiagnosisRunResponse getRun(UUID diagnosisId) {
        DiagnosisRun byId = diagnosisRunService.getById(diagnosisId);
        if (byId == null){
            throw  new BusinessException(ErrorCode.DIAGNOSIS_RUN_NOT_FOUND,"诊断不存在");
        }
        DiagnosisRunResponse diagnosisRunDTO = BeanUtil.copyProperties(byId, DiagnosisRunResponse.class);
        ChatMessage msg = chatMessageService.getById(byId.getResponseMessageId());
        if(msg == null){
            log.error(
                    "Diagnosis response message missing, diagnosisId={}, responseMessageId={}",
                    byId.getId(),
                    byId.getResponseMessageId()
            );
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        diagnosisRunDTO.setResponse(msg.getContent());
        return diagnosisRunDTO;
    }

    /**
     * 构建提示词，并创建本次运行需要的所有持久化对象。
     *
     * <p>这里故意不使用 {@code @Transactional}。事务入口位于独立的
     * {@link DiagnosisRunLifecycleService} Spring Bean 中，确保代理能够生效；
     * 如果把注解放到本类的 private 方法上，自调用不会经过 Spring 代理。</p>
     */
    private PreparedDiagnosis prepareRun(
            String input,
            ChatConversation conversation,
            ProjectInput projectInput,
            ChatAttachment attachment
    ) {
        List<ChatMessage> messageHistory = chatMessageService
                .gethistory(conversation.getId());
        long requestSequenceNo = messageHistory.size() + 1L;

        ChatMessage requestMessage = new ChatMessage()
                .setId(UUID.randomUUID())
                .setConversationId(conversation.getId())
                .setSenderRole(SenderRole.USER)
                .setContent(input)
                .setParentMessageId(lastMessageId(messageHistory))
                .setSequenceNo(requestSequenceNo)
                .setStatus(MessageStatus.COMPLETED);

        ChatMessage responseMessage = new ChatMessage()
                .setId(UUID.randomUUID())
                .setConversationId(conversation.getId())
                .setSenderRole(SenderRole.ASSISTANT)
                .setParentMessageId(requestMessage.getId())
                .setSequenceNo(requestSequenceNo + 1L)
                .setContent("")
                .setModelProvider(MODEL_PROVIDER)
                .setModelName(modelName())
                .setStatus(MessageStatus.PENDING);

        if (attachment != null) {
            attachment.setMessageId(requestMessage.getId());
        }

        DiagnosisRun diagnosisRun = new DiagnosisRun()
                .setId(UUID.randomUUID())
                .setConversationId(conversation.getId())
                .setRequestMessageId(requestMessage.getId())
                .setResponseMessageId(responseMessage.getId())
                .setStatus(DiagnosisRunStatus.QUEUED.name())
                .setQuestion(input)
                .setProjectSnapshot(projectInput == null
                        ? JsonNodeFactory.instance.objectNode()
                        : objectMapper.valueToTree(projectInput))
                .setTargetSnapshot(JsonNodeFactory.instance.objectNode())
                .setModelProvider(MODEL_PROVIDER)
                .setModelName(modelName())
                .setPromptVersion(PROMPT_VERSION);

        diagnosisRunLifecycleService.createRun(
                requestMessage,
                responseMessage,
                attachment,
                diagnosisRun
        );

        try {
            // 混合检索（向量 + 关键词 + RRF）：基于完整知识库检索，
            // 不再限定单一文档来源
            List<Document> documents = knowledgeRetrievalService
                    .search(new RetrievalRequest(
                            input,
                            RETRIEVAL_TOP_K,
                            RETRIEVAL_MIN_SCORE,
                            Map.of()
                    ))
                    .evidences()
                    .stream()
                    .map(RetrievedEvidence::document)
                    .toList();
            DiagnosisPromptContext promptContext = new DiagnosisPromptContext(
                    input,
                    messageHistory,
                    documents,
                    Optional.ofNullable(projectInput)
            );
            Prompt prompt = diagnosisPromptBuilder.build(promptContext);
            return new PreparedDiagnosis(prompt, diagnosisRun, documents);
        } catch (RuntimeException error) {
            persistPreparationFailure(diagnosisRun, error);
            throw error;
        }
    }

    private DiagnosisStream toDiagnosisStream(
            ChatConversation conversation,
            PreparedDiagnosis prepared
    ) {
        return new DiagnosisStream(
                prepared.diagnosisRun().getId(),
                conversation.getId(),
                streamDiagnosis(prepared)
        );
    }

    /**
     * 在订阅时启动模型，并根据 Reactor 的终止信号更新运行记录。
     *
     * <p>{@code subscribeOn(boundedElastic)} 负责把订阅阶段的 markRunning JDBC
     * 更新移出 MVC 订阅线程；{@code publishOn(boundedElastic)} 则让模型发出数据后的
     * 文本拼接和完成/失败写入避开模型客户端事件线程。取消回调不保证运行在
     * publishOn 选择的线程上，因此取消写入会再次调度到 boundedElastic；它仍然
     * 属于尽力执行的清理操作，应用进程被强制终止时不能保证一定落库。</p>
     */
    private Flux<String> streamDiagnosis(
            PreparedDiagnosis prepared
    ) {
        return Flux.defer(() -> {
            StringBuilder fullContent = new StringBuilder();
            DiagnosisRun diagnosisRun = prepared.diagnosisRun();

            Flux<String> modelStream = Flux.defer(() -> {
                        diagnosisRun.setStartedAt(OffsetDateTime.now());
                        diagnosisRunLifecycleService.markRunning(diagnosisRun);
                        return chatModel.stream(prepared.prompt());
                    })
                    .publishOn(Schedulers.boundedElastic())
                    .map(this::extractText)
                    .filter(text -> !text.isEmpty())
                    .doOnNext(fullContent::append);

            return modelStream
                    .concatWith(Mono.defer(() -> {
                        String modelOutput = fullContent.toString();

                        DiagnosisResult result =
                                diagnosisResultStructuringService.structure(
                                        modelOutput,
                                        prepared.documents()
                                );

                        persistSuccessfulRun(
                                diagnosisRun,
                                result,
                                modelOutput
                        );

                        return Mono.<String>empty();
                    }))
                    .doOnError(error -> persistFailure(
                            diagnosisRun,
                            fullContent.toString(),
                            error
                    ))
                    .doOnCancel(() -> persistCancellation(
                            diagnosisRun,
                            fullContent.toString()
                    ));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 异常回调不能再抛出新的持久化异常，否则会掩盖模型调用的原始异常。
     */
    private void persistFailure(
            DiagnosisRun diagnosisRun,
            String partialContent,
            Throwable error
    ) {
        try {
            diagnosisRunLifecycleService.markFailed(
                    diagnosisRun,
                    partialContent,
                    failureErrorCode(error),
                    errorMessage(error),
                    OffsetDateTime.now()
            );
        } catch (RuntimeException persistenceError) {
            log.error(
                    "Failed to persist failed diagnosis run, diagnosisId={}",
                    diagnosisRun.getId(),
                    persistenceError
            );
        }
    }

    /**
     * 将完成阶段的数据库异常包装为独立类型，避免与模型流异常混淆。
     */
    private void persistSuccessfulRun(
            DiagnosisRun diagnosisRun,
            DiagnosisResult result,
            String modelOutput
    ) {
        try {
            diagnosisResultPersistenceService.save(
                    diagnosisRun,
                    result,
                    modelOutput,
                    OffsetDateTime.now()
            );
        } catch (RuntimeException error) {
            throw new DiagnosisResultPersistenceException(
                    "结构化诊断结果持久化失败",
                    error
            );
        }
    }

    private String failureErrorCode(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof DiagnosisResultParseException) {
                return RESULT_PARSE_ERROR_CODE;
            }
            if (current instanceof DiagnosisResultPersistenceException) {
                return RESULT_PERSIST_ERROR_CODE;
            }
            current = current.getCause();
        }
        return STREAM_ERROR_CODE;
    }

    /**
     * 检索知识库或构建 Prompt 失败时，流还没有创建，因此要在当前线程直接结束运行。
     */
    private void persistPreparationFailure(
            DiagnosisRun diagnosisRun,
            Throwable error
    ) {
        try {
            diagnosisRunLifecycleService.markFailed(
                    diagnosisRun,
                    "",
                    PREPARATION_ERROR_CODE,
                    errorMessage(error),
                    OffsetDateTime.now()
            );
        } catch (RuntimeException persistenceError) {
            log.error(
                    "Failed to persist diagnosis preparation failure, diagnosisId={}",
                    diagnosisRun.getId(),
                    persistenceError
            );
        }
    }

    /**
     * 取消信号可能来自 Tomcat 请求线程或其他线程，所以显式交给可阻塞调度器保存。
     */
    private void persistCancellation(
            DiagnosisRun diagnosisRun, String partialContent)
    {
        Schedulers.boundedElastic().schedule(() -> {
            try {
                diagnosisRunLifecycleService.markCancelled(
                        diagnosisRun,
                        partialContent,
                        CANCEL_DETAIL,
                        OffsetDateTime.now()
                );
            } catch (RuntimeException persistenceError) {
                log.error(
                        "Failed to persist cancelled diagnosis run, diagnosisId={}",
                        diagnosisRun.getId(),
                        persistenceError
                );
            }
        });
    }

    /**
     * 从模型响应中安全提取当前文本片段。
     */
    private String extractText(ChatResponse response) {
        if (CollectionUtil.isEmpty(response.getResults())) {
            return "";
        }
        String text = response.getResult().getOutput().getText();
        return text == null ? "" : text;
    }

    private UUID lastMessageId(List<ChatMessage> messageHistory) {
        return messageHistory.isEmpty()
                ? null
                : messageHistory.get(messageHistory.size() - 1).getId();
    }

    private String modelName() {
        return chatModel.getDefaultOptions() == null
                ? null
                : chatModel.getDefaultOptions().getModel();
    }

    private String errorMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : message;
    }
}
