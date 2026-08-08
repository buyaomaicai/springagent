package com.springagent.admin.domain.vo;

import com.springagent.diagnosis.entity.ChatMessage;
import lombok.Data;
import org.springframework.ai.chat.messages.Message;

import java.util.List;
import java.util.UUID;

@Data
public class ConversationVO {
    private UUID conversionId;
    private String conversationName;
    private List<ChatMessage> messages;
}
