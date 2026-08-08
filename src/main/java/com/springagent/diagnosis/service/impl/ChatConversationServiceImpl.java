package com.springagent.diagnosis.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.springagent.admin.domain.vo.ConversationListVO;
import com.springagent.admin.domain.vo.ConversationVO;
import com.springagent.common.api.ErrorCode;
import com.springagent.common.exception.BusinessException;
import com.springagent.diagnosis.entity.ChatConversation;
import com.springagent.diagnosis.entity.ChatMessage;
import com.springagent.diagnosis.mapper.ChatConversationMapper;
import com.springagent.diagnosis.mapper.ChatMessageMapper;
import com.springagent.diagnosis.service.IChatConversationService;

import com.springagent.diagnosis.service.IChatMessageService;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.BadRequestException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * <p>
 * Durable user-visible conversation history. Do not use as an evicting LLM memory window. 服务实现类
 * </p>
 *
 * @author author
 * @since 2026-07-24
 */
@RequiredArgsConstructor
@Service
public class ChatConversationServiceImpl extends ServiceImpl<ChatConversationMapper, ChatConversation> implements IChatConversationService {
    private  final ChatConversationMapper chatConversationMapper;
    private final ChatMessageMapper chatMessageMapper;
    public static final UUID TEST_USER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    @Override
    public ChatConversation getOrCreateConversation(UUID conversationId) {
        if (conversationId == null) {
            ChatConversation chatConversation = new ChatConversation();
            chatConversation.setUserId(TEST_USER_ID);
            save(chatConversation);
            return chatConversation;
        }
        return getById(conversationId);
    }

    @Override
    public List<ConversationListVO> getConversionList() {
        ConversationListVO conversionListVO = new ConversationListVO();
        List<ConversationListVO> list = chatConversationMapper.selectGroupByUser();
        return CollectionUtil.emptyIfNull(list);
    }

    @Override
    public List<ConversationVO> getConversionByUserId(UUID id)  {
        List<ChatConversation> list = lambdaQuery()
                .eq(ChatConversation::getUserId, id)
                .list();
        if (CollectionUtil.isEmpty(list)){
            throw new BusinessException(ErrorCode.INVALID_REQUEST,"用户会话不存在");
        }
        List<ConversationVO> conversionVOS = new ArrayList<>();
        List<UUID> conversionIds = new ArrayList<>();
        for (ChatConversation chatConversation : list) {
            ConversationVO conversionVO = new ConversationVO();
            conversionVO.setConversionId(chatConversation.getId());
            conversionVO.setConversationName(chatConversation.getTitle());
            conversionVOS.add(conversionVO);
            conversionIds.add(chatConversation.getId());
        }
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(ChatMessage::getConversationId, conversionIds)
                .orderByAsc(ChatMessage::getConversationId, ChatMessage::getSequenceNo);
        List<ChatMessage> chatMessages = chatMessageMapper.selectList(wrapper);
        for (ConversationVO conversionVO : conversionVOS) {
            List<ChatMessage> messages = chatMessages.stream()
                    .filter(message -> message.getConversationId().equals(conversionVO.getConversionId()))
                    .toList();
            conversionVO.setMessages(messages);
        }
        return conversionVOS;
    }
}
