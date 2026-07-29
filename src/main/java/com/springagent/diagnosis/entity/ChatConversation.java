package com.springagent.diagnosis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.springagent.common.persistence.JsonbTypeHandler;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * Durable user-visible conversation history. Do not use as an evicting LLM memory window.
 * </p>
 *
 * @author author
 * @since 2026-07-24
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName(value = "chat_conversation", autoResultMap = true)
public class ChatConversation implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.INPUT)
    private UUID id = UUID.randomUUID();

    private UUID userId;

    private String title;

    private String status;

    private String projectName;

    private String currentJdkVersion;

    private String currentSpringBootVersion;

    private String targetJdkVersion;

    private String targetSpringBootVersion;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private JsonNode metadata;

    private OffsetDateTime lastMessageAt;

    private Integer lockVersion;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private OffsetDateTime deletedAt;


}
