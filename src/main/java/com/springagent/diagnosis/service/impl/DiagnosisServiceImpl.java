package com.springagent.diagnosis.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.springagent.ai.PromptType;
import com.springagent.ai.prompt.DiagnosisPromptStrategy;
import com.springagent.common.Constant.MessageStatus;
import com.springagent.common.Constant.SenderRole;
import com.springagent.diagnosis.domain.dto.request.DiagnosisRequest;
import com.springagent.diagnosis.entity.ChatConversation;
import com.springagent.diagnosis.entity.ChatMessage;
import com.springagent.diagnosis.service.IChatConversationService;
import com.springagent.diagnosis.service.IChatMessageService;
import com.springagent.diagnosis.service.IDiagnosisService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class DiagnosisServiceImpl implements IDiagnosisService {
    private final DeepSeekChatModel chatModel;
    private final IChatConversationService chatConversationService;
    private final IChatMessageService chatMessageService;
    @Override
    public Flux<String> callDiagnosis(DiagnosisRequest request) {
        String prompt = DiagnosisPromptStrategy.getPrompt(PromptType.Diagnosis);
        ChatConversation conversation = chatConversationService
                .getOrCreateConversation(request.getConversationId());
        List<Message> history = new ArrayList<>();
        history.add(new SystemMessage(prompt));
        List<ChatMessage> messageHistory = chatMessageService
                .gethistory(conversation.getId());
        long userSequenceNo = messageHistory.size() + 1L;
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setId(UUID.randomUUID());
        chatMessage.setConversationId(conversation.getId());
        chatMessage.setSenderRole(SenderRole.USER);
        chatMessage.setContent(request.getInput());
        chatMessage.setParentMessageId(messageHistory.isEmpty() ? null : messageHistory.getLast().getId());
        chatMessage.setSequenceNo(userSequenceNo);
        List<Message> list = messageHistory.stream()
                .map(message -> message.getSenderRole().toAiMessage(message.getContent()))
                .toList();
        history.addAll(1,list);
        history.add(new UserMessage(request.getInput()));
        Prompt aiPrompt = new Prompt(history);
        chatMessageService.save(chatMessage);

        return Flux.defer(() -> {
            StringBuilder fullContent = new StringBuilder();
            ChatMessage assistant = new ChatMessage();
            assistant.setId(UUID.randomUUID());
            assistant.setConversationId(conversation.getId());
            assistant.setSenderRole(SenderRole.ASSISTANT);
            assistant.setParentMessageId(chatMessage.getId());
            assistant.setSequenceNo(userSequenceNo + 1);

            return chatModel.stream(aiPrompt)
                    .publishOn(Schedulers.boundedElastic())
                    .map(this::extractText)
                    .filter(text -> !text.isEmpty())
                    .doOnNext(fullContent::append)
                    .doOnComplete(() -> chatMessageService.save(assistant
                            .setStatus(MessageStatus.COMPLETED)
                            .setContent(fullContent.toString())))
                    .doOnError(error -> chatMessageService.save(assistant
                            .setStatus(MessageStatus.FAILED)
                            .setContent(fullContent.toString())
                            .setErrorMessage(error.getMessage())))
                    .doOnCancel(() -> chatMessageService.save(assistant
                            .setStatus(MessageStatus.FAILED)
                            .setContent(fullContent.toString())
                            .setErrorMessage("会话取消")));
        });

    }
    private String extractText(ChatResponse response) {
        if (CollectionUtil.isEmpty(response.getResults())) {
            return "";
        }

        String text = response.getResult()
                .getOutput()
                .getText();

        return text == null ? "" : text;
    }
}
