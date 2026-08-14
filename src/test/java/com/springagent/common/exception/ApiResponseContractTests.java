package com.springagent.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.springagent.common.api.ApiResponse;
import com.springagent.common.api.ErrorCode;
import com.springagent.common.web.RequestIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class ApiResponseContractTests {

    private static final String REQUEST_ID = "contract-test-request";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ContractController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void returnsStandardSuccessEnvelopeAndRequestId() throws Exception {
        mockMvc.perform(get("/contract/success")
                        .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIdFilter.HEADER_NAME, REQUEST_ID))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.value").value("ready"))
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void returnsFieldErrorsForInvalidRequest() throws Exception {
        mockMvc.perform(post("/contract/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errors[0].field").value("input"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("input must not be blank"));
    }

    @Test
    void returnsStableBusinessErrorAndHttpStatus() throws Exception {
        mockMvc.perform(get("/contract/not-implemented"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.code").value("NOT_IMPLEMENTED"))
                .andExpect(jsonPath("$.message").value("接口尚未实现"));
    }

    @Test
    void hidesUnexpectedExceptionDetails() throws Exception {
        mockMvc.perform(get("/contract/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("服务器内部错误"))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("database password")
                        )
                ));
    }

    @Test
    void returnsStandardErrorForMissingParameter() throws Exception {
        mockMvc.perform(get("/contract/required"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("value"));
    }

    @Test
    void returnsStandardErrorForUnsupportedMethod() throws Exception {
        mockMvc.perform(post("/contract/success"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void returnsStandardErrorForUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/contract/validate")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("input"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @RestController
    @RequestMapping("/contract")
    private static class ContractController {

        @GetMapping("/success")
        ApiResponse<Map<String, String>> success() {
            return ApiResponse.success(Map.of("value", "ready"));
        }

        @GetMapping("/required")
        ApiResponse<String> required(@RequestParam String value) {
            return ApiResponse.success(value);
        }

        @PostMapping("/validate")
        ApiResponse<String> validate(@Valid @RequestBody ContractRequest request) {
            return ApiResponse.success(request.input());
        }

        @GetMapping("/not-implemented")
        ApiResponse<Void> notImplemented() {
            throw new BusinessException(ErrorCode.NOT_IMPLEMENTED);
        }

        @GetMapping("/unexpected")
        ApiResponse<Void> unexpected() {
            throw new IllegalStateException("database password was exposed");
        }
    }

    private record ContractRequest(
            @NotBlank(message = "input must not be blank") String input
    ) {
    }
}
