package com.springagent.diagnosis.controller;

import cn.hutool.core.bean.BeanUtil;
import com.springagent.common.api.ApiResponse;
import com.springagent.common.api.ErrorCode;
import com.springagent.common.exception.BusinessException;
import com.springagent.common.web.RequestIdContext;
import com.springagent.diagnosis.domain.dto.DiagnosisParserDTO;
import com.springagent.diagnosis.domain.dto.DiagnosisRunDTO;
import com.springagent.diagnosis.domain.dto.request.DiagnosisParserRequest;
import com.springagent.diagnosis.domain.dto.request.DiagnosisRequest;
import com.springagent.diagnosis.domain.dto.response.HealthResponse;
import com.springagent.diagnosis.domain.dto.stream.StreamChunk;
import com.springagent.diagnosis.domain.dto.stream.StreamCompleted;
import com.springagent.diagnosis.domain.dto.stream.StreamError;
import com.springagent.diagnosis.domain.dto.stream.StreamEvent;
import com.springagent.diagnosis.domain.dto.stream.StreamMetadata;
import com.springagent.diagnosis.model.DiagnosisStream;
import com.springagent.diagnosis.service.IDiagnosisService;
import jakarta.validation.Valid;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
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
        DiagnosisStream diagnosisStream = diagnosisService
                .callDiagnosis(request);

        ServerSentEvent<Object> metadataEvent = toEvent(
                "meta",
                StreamEvent.of(
                        requestId,
                        sequence.getAndIncrement(),
                        new StreamMetadata(
                                STREAM_PROTOCOL_VERSION,
                                diagnosisStream.conversationId(),
                                diagnosisStream.diagnosisId()
                        )
                )
        );

        Flux<ServerSentEvent<Object>> contentEvents = diagnosisStream
                .content()
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
    @PostMapping(value = "/prepare"
    , consumes = MediaType.MULTIPART_FORM_DATA_VALUE ,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<ServerSentEvent<Object>> prepare(
            @ModelAttribute @Valid DiagnosisParserRequest request
    ) {
        MultipartFile file = request.getFile();
        DiagnosisParserDTO diagnosisParserdto = BeanUtil.copyProperties(request, DiagnosisParserDTO.class);
        diagnosisParserdto.setFileName(file.getOriginalFilename());
        diagnosisParserdto.setMediaType(file.getContentType());
        try {
            diagnosisParserdto.setContent(file.getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        String requestId = RequestIdContext.current();
        AtomicLong sequence = new AtomicLong();
        DiagnosisStream diagnosisStream = diagnosisService
                .prepare(diagnosisParserdto);

        ServerSentEvent<Object> metadataEvent = toEvent(
                "meta",
                StreamEvent.of(
                        requestId,
                        sequence.getAndIncrement(),
                        new StreamMetadata(
                                STREAM_PROTOCOL_VERSION,
                                diagnosisStream.conversationId(),
                                diagnosisStream.diagnosisId()
                        )
                )
        );

        Flux<ServerSentEvent<Object>> contentEvents = diagnosisStream
                .content()
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
    @GetMapping("/runs/{diagnosisId}")
    public ApiResponse<DiagnosisRunDTO> getRun(@PathVariable UUID diagnosisId) {
        return ApiResponse.success(diagnosisService.getRun(diagnosisId));
    }
}
