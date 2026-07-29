package com.springagent.diagnosis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.springagent.common.Constant.MessageStatus;
import com.springagent.common.Constant.SenderRole;
import com.springagent.common.persistence.JsonbTypeHandler;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * Immutable ordered messages, including user, assistant, system, and tool messages.
 * </p>
 *
 * @author author
 * @since 2026-07-24
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName(value = "chat_message", autoResultMap = true)
public class ChatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.INPUT)
    private UUID id = UUID.randomUUID();

    private UUID conversationId;

    private UUID parentMessageId;

    private Long sequenceNo;

    private SenderRole senderRole;

    private String messageType;

    private String contentFormat;

    private String content;

    private String modelProvider;

    private String modelName;

    private Integer inputTokens;

    private Integer outputTokens;

    private Integer latencyMs;

    private MessageStatus status;

    private String errorMessage;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private JsonNode metadata;

    private OffsetDateTime createdAt;


}
