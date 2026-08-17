package com.springagent.diagnosis.service;

import com.springagent.diagnosis.domain.dto.result.DiagnosisResult;

public interface IDiagnosisResultStructuringService {

    DiagnosisResult structure(String diagnosisContent);
}