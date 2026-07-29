package com.springagent.diagnosis.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.springagent.diagnosis.entity.ChatConversation;

import java.util.UUID;

/**
 * <p>
 * Durable user-visible conversation history. Do not use as an evicting LLM memory window. 服务类
 * </p>
 *
 * @author author
 * @since 2026-07-24
 */
public interface IChatConversationService extends IService<ChatConversation> {

    ChatConversation getOrCreateConversation(UUID conversationId);
}
