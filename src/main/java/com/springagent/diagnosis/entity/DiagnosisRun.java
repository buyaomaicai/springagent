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
 * One upgrade diagnosis execution with immutable input snapshots and structured output.
 * </p>
 *
 * @author author
 * @since 2026-07-24
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName(value = "diagnosis_run", autoResultMap = true)
public class DiagnosisRun implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.INPUT)
    private UUID id = UUID.randomUUID();

    private UUID conversationId;

    private UUID requestMessageId;

    private UUID responseMessageId;

    private String status;

    private String question;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private JsonNode projectSnapshot;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private JsonNode targetSnapshot;

    private String modelProvider;

    private String modelName;

    private String promptVersion;

    private String summary;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private JsonNode rawResult;

    private String errorCode;

    private String errorDetail;

    private OffsetDateTime startedAt;

    private OffsetDateTime completedAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;


}
