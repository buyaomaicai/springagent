package com.springagent.diagnosis.service.impl;

import com.springagent.diagnosis.domain.dto.result.DiagnosisResult;
import com.springagent.diagnosis.service.IDiagnosisResultStructuringService;
import com.springagent.parser.DiagnosisResultParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DiagnosisResultStructuringService implements IDiagnosisResultStructuringService {
    private final DiagnosisResultParser diagnosisResultParser;
    @Override
    public DiagnosisResult structure(String diagnosisContent) {
        return diagnosisResultParser.parse(diagnosisContent);
    }
}
