package com.springagent.diagnosis.service;

import com.springagent.diagnosis.domain.dto.DiagnosisParserDTO;
import com.springagent.diagnosis.domain.dto.request.DiagnosisRequest;
import com.springagent.diagnosis.model.DiagnosisStream;


public interface IDiagnosisService {
    DiagnosisStream callDiagnosis(DiagnosisRequest request);

    DiagnosisStream prepare(DiagnosisParserDTO request);
}
