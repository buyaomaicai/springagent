package com.springagent.diagnosis.service;

import com.springagent.diagnosis.domain.dto.request.DiagnosisRequest;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;


public interface IDiagnosisService {
    Flux<String> callDiagnosis(DiagnosisRequest request);


}
