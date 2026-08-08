package com.springagent.diagnosis.controller;

import com.springagent.common.api.ApiResponse;
import com.springagent.common.api.ErrorCode;
import com.springagent.common.exception.BusinessException;
import com.springagent.common.web.RequestIdContext;
import com.springagent.diagnosis.domain.dto.request.DiagnosisRequest;
import com.springagent.diagnosis.domain.dto.response.HealthResponse;
import com.springagent.diagnosis.domain.dto.stream.StreamChunk;
import com.springagent.diagnosis.domain.dto.stream.StreamCompleted;
import com.springagent.diagnosis.domain.dto.stream.StreamError;
import com.springagent.diagnosis.domain.dto.stream.StreamEvent;
import com.springagent.diagnosis.domain.dto.stream.StreamMetadata;
import com.springagent.diagnosis.service.IDiagnosisService;
import jakarta.validation.Valid;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Slf4j
@RestController
@RequestMapping("/diagnosis")
@RequiredArgsConstructor
public class DiagnosisController {

    private static final String STREAM_PROTOCOL_VERSION = "1.0";

    private final IDiagnosisService diagnosisService;

    @GetMapping("/health")
    public ApiResponse<HealthResponse> healthCheck() {
        return ApiResponse.success(new HealthResponse("UP"));
    }

    @PostMapping(
            value = "/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> callDiagnosis(
            @RequestBody @Valid DiagnosisRequest request
    ) {
        String requestId = RequestIdContext.current();
        AtomicLong sequence = new AtomicLong();

        ServerSentEvent<Object> metadataEvent = toEvent(
                "meta",
                StreamEvent.of(
                        requestId,
                        sequence.getAndIncrement(),
                        new StreamMetadata(
                                STREAM_PROTOCOL_VERSION,
                                request.getConversationId()
                        )
                )
        );

        Flux<ServerSentEvent<Object>> contentEvents = diagnosisService
                .callDiagnosis(request)
                .map(content -> toEvent(
                        "chunk",
                        StreamEvent.of(
                                requestId,
                                sequence.getAndIncrement(),
                                new StreamChunk(content)
                        )
                ))
                .concatWith(Flux.defer(() -> Flux.just(toEvent(
                        "done",
                        StreamEvent.of(
                                requestId,
                                sequence.getAndIncrement(),
                                new StreamCompleted("COMPLETED")
                        )
                ))))
                .onErrorResume(error -> {
                    log.error("Diagnosis stream failed, requestId={}", requestId, error);
                    return Flux.just(toErrorEvent(requestId, sequence, error));
                });

        return Flux.concat(Flux.just(metadataEvent), contentEvents);
    }

    private ServerSentEvent<Object> toErrorEvent(
            String requestId,
            AtomicLong sequence,
            Throwable error
    ) {
        ErrorCode errorCode = ErrorCode.DIAGNOSIS_STREAM_FAILED;
        String message = errorCode.defaultMessage();

        if (error instanceof BusinessException businessException) {
            errorCode = businessException.getErrorCode();
            message = businessException.getMessage();
        }

        return toEvent(
                "error",
                StreamEvent.of(
                        requestId,
                        sequence.getAndIncrement(),
                        new StreamError(
                                errorCode.name(),
                                message,
                                errorCode.retryable()
                        )
                )
        );
    }

    private ServerSentEvent<Object> toEvent(
            String eventName,
            StreamEvent<?> event
    ) {
        return ServerSentEvent.builder((Object) event)
                .event(eventName)
                .build();
    }
}
