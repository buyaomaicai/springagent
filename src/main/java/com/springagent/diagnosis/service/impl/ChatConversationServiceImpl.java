package com.springagent.diagnosis.service.impl;


import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.springagent.diagnosis.entity.ChatConversation;
import com.springagent.diagnosis.mapper.ChatConversationMapper;
import com.springagent.diagnosis.service.IChatConversationService;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * <p>
 * Durable user-visible conversation history. Do not use as an evicting LLM memory window. 服务实现类
 * </p>
 *
 * @author author
 * @since 2026-07-24
 */
@Service
public class ChatConversationServiceImpl extends ServiceImpl<ChatConversationMapper, ChatConversation> implements IChatConversationService {

    @Override
    public ChatConversation getOrCreateConversation(UUID conversationId) {
        if (conversationId == null) {
            ChatConversation chatConversation = new ChatConversation();
            save(chatConversation);
            return chatConversation;
        }
        return getById(conversationId);
    }
}
