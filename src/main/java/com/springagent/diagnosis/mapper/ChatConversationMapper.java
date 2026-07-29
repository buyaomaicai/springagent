package com.springagent.diagnosis.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.springagent.diagnosis.entity.ChatConversation;

/**
 * <p>
 * Durable user-visible conversation history. Do not use as an evicting LLM memory window. Mapper 接口
 * </p>
 *
 * @author author
 * @since 2026-07-24
 */
public interface ChatConversationMapper extends BaseMapper<ChatConversation> {

}
