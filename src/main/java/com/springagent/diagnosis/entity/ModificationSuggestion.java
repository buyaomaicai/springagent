package com.springagent.diagnosis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
@TableName("modification_suggestion")
public class ModificationSuggestion implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.INPUT)
    private UUID id = UUID.randomUUID();

    private UUID diagnosisId;

    private String priority;

    private String actionType;

    private String filePath;

    private String title;

    private String description;

    private String beforeContent;

    private String afterContent;

    private String verification;

    private String status;

    private Integer sortOrder;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;


}
