package com.springagent.diagnosis.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.springagent.admin.domain.vo.ConversationListVO;
import com.springagent.diagnosis.entity.ChatConversation;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.type.ArrayTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.util.List;

/**
 * <p>
 * Durable user-visible conversation history. Do not use as an evicting LLM memory window. Mapper 接口
 * </p>
 *
 * @author author
 * @since 2026-07-24
 */
public interface ChatConversationMapper extends BaseMapper<ChatConversation> {
    @Results({
            @Result(column = "user_id", property = "userId"),
            @Result(column = "user_name", property = "userName"),
            @Result(
                    column = "conversation_count",
                    property = "conversationCount"
            ),
            @Result(
                    column = "conversation_ids",
                    property = "conversationIds",
                    jdbcType = JdbcType.ARRAY,
                    typeHandler = ArrayTypeHandler.class
            )
    })
    @Select("""
        SELECT
            c.user_id AS user_id,
            u.display_name AS user_name,
            COUNT(*) AS conversation_count,
            ARRAY_AGG(c.id ORDER BY c.created_at) AS conversation_ids
        FROM chat_conversation c
        JOIN app_user u ON c.user_id = u.id
        WHERE c.deleted_at IS NULL
        GROUP BY c.user_id, u.display_name
        """)
    List<ConversationListVO> selectGroupByUser();
}
