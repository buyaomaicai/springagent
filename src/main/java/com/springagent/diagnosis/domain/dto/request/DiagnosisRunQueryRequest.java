package com.springagent.diagnosis.domain.dto.request;

import com.springagent.diagnosis.model.DiagnosisRunStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Data;

/**
 * 按会话分页查询诊断运行的参数。
 */
@Data
public class DiagnosisRunQueryRequest {

    @NotNull(message = "会话 ID 不能为空")
    private UUID conversationId;

    @Min(value = 1, message = "页码必须大于等于 1")
    private int page = 1;

    @Min(value = 1, message = "每页数量必须大于等于 1")
    @Max(value = 100, message = "每页数量不能超过 100")
    private int size = 20;

    private DiagnosisRunStatus status;
}
