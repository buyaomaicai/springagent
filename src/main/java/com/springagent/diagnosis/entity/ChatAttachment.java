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
 * 
 * </p>
 *
 * @author author
 * @since 2026-07-24
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName(value = "chat_attachment", autoResultMap = true)
public class ChatAttachment implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.INPUT)
    private UUID id = UUID.randomUUID();

    private UUID messageId;

    private String artifactType;

    private String fileName;

    private String mediaType;

    private Long fileSizeBytes;

    private String sha256;

    private String contentText;

    private String storageUri;

    private String extractionStatus;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private JsonNode parsedContent;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private JsonNode metadata;

    private OffsetDateTime createdAt;


}
