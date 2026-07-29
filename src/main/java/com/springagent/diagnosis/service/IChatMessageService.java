package com.springagent.diagnosis.service;


import com.baomidou.mybatisplus.spring.service.IService;
import com.springagent.diagnosis.entity.ChatMessage;
import lombok.NonNull;
import org.springframework.ai.chat.messages.Message;

import java.util.List;
import java.util.UUID;

/**
 * <p>
 * Immutable ordered messages, including user, assistant, system, and tool messages. 服务类
 * </p>
 *
 * @author author
 * @since 2026-07-24
 */
public interface IChatMessageService extends IService<ChatMessage> {

    List<ChatMessage> gethistory(UUID conversationId);


}
