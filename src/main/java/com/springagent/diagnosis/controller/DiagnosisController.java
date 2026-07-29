package com.springagent.diagnosis.controller;

import com.springagent.diagnosis.domain.dto.request.DiagnosisRequest;
import com.springagent.diagnosis.service.IDiagnosisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import javax.validation.Valid;

@RestController
@RequestMapping("/diagnosisController")
@RequiredArgsConstructor
public class DiagnosisController {
    private final IDiagnosisService diagnosisService;
    @GetMapping("/health")
    public String healthCheck(){
        return "this is live";
    }
    @PostMapping(
            value = "/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> CallDiagnosis(@RequestBody @Valid DiagnosisRequest request){
        return diagnosisService.callDiagnosis(request)
                .map(text -> ServerSentEvent.<String>builder()
                        .event("chunk")
                        .data(text)
                        .build())
                .concatWithValues(
                        ServerSentEvent.<String>builder()
                                .event("done")
                                .data("")
                                .build())
                .onErrorResume(error ->//把java异常转换为前端能收到的error事件
                        Flux.just(
                                ServerSentEvent.<String>builder()
                                        .event("error")
                                        .data("诊断生成失败")
                                        .build()));
    }
    /*
    * produces 声明是SSE流，不是普通json
    * ServerSentEvent.builder():构建一个SSE事件
    * event("chunk")设置一个事件名称
    * data（text）：设置本次事件的数据
    * concatWithValues(
                        ServerSentEvent.<String>builder()
                                .event("done")
                                .data("")
                                .build())  上游正常完成后追加一个done事件
    * */
}
