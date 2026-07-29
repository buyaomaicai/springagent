package com.springagent.common.Constant;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * 消息发送方角色。
 * <p>
 * 取值必须与数据库 chat_message 表的 chat_message_sender_role_chk 约束保持一致，
 * 即 SYSTEM / USER / ASSISTANT / TOOL 四种，不要随意增加值。
 * 消息的错误情况请使用 status = 'FAILED' 或 message_type = 'ERROR' 表达。
 */
public enum SenderRole {

    SYSTEM,
    USER,
    ASSISTANT,
    TOOL;

    /**
     * 按角色将持久化的消息内容转换为 Spring AI 的 Message，用于拼装对话历史。
     */
    public Message toAiMessage(String content) {
        return switch (this) {
            case SYSTEM -> new SystemMessage(content);
            case USER -> new UserMessage(content);
            case ASSISTANT -> new AssistantMessage(content);
            case TOOL -> throw new UnsupportedOperationException(
                    "TOOL 消息需携带工具调用结果，请单独构造 ToolResponseMessage");
        };
    }
}
