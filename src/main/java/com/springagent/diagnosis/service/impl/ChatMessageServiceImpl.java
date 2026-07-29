package com.springagent.diagnosis.service.impl;


import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.springagent.diagnosis.entity.ChatMessage;
import com.springagent.diagnosis.mapper.ChatMessageMapper;
import com.springagent.diagnosis.service.IChatMessageService;
import lombok.NonNull;
import org.springframework.ai.chat.messages.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.springagent.common.Constant.SenderRole.*;

/**
 * <p>
 * Immutable ordered messages, including user, assistant, system, and tool messages. 服务实现类
 * </p>
 *
 * @author author
 * @since 2026-07-24
 */
@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage> implements IChatMessageService {

    @Override
    public List<ChatMessage> gethistory(UUID conversationId) {
        List<ChatMessage> list = lambdaQuery()
                .eq(ChatMessage::getConversationId, conversationId)
                .orderByAsc(ChatMessage::getSequenceNo)
                .list();
        return list;
    }

}
