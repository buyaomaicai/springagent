package com.springagent.diagnosis.service;

import com.springagent.common.api.PageResponse;
import com.springagent.diagnosis.domain.dto.response.DiagnosisResultResponse;
import com.springagent.diagnosis.domain.dto.response.DiagnosisRunSummaryResponse;
import com.springagent.diagnosis.model.DiagnosisRunStatus;
import java.util.UUID;

public interface IDiagnosisResultQueryService {

    PageResponse<DiagnosisRunSummaryResponse> listRuns(
            UUID conversationId,
            int page,
            int size,
            DiagnosisRunStatus status
    );

    DiagnosisResultResponse getResult(UUID diagnosisId);
}
