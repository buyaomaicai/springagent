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
@TableName(value = "upgrade_plan_step", autoResultMap = true)
public class UpgradePlanStep implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.INPUT)
    private UUID id = UUID.randomUUID();

    private UUID diagnosisId;

    private Integer sequenceNo;

    private String phase;

    private String title;

    private String description;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private JsonNode prerequisites;

    private String verification;

    private String rollbackAction;

    private String estimatedEffort;

    private String status;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;


}
