package com.springagent.common.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.springagent.common.web.RequestIdContext;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        String code,
        String message,
        T data,
        List<ApiErrorDetail> errors,
        String requestId,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant timestamp
) {

    public ApiResponse {
        errors = errors == null ? null : List.copyOf(errors);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(
                ErrorCode.OK.name(),
                ErrorCode.OK.defaultMessage(),
                data,
                null,
                RequestIdContext.current(),
                Instant.now()
        );
    }

    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return error(errorCode, errorCode.defaultMessage(), null);
    }

    public static ApiResponse<Void> error(ErrorCode errorCode, String message) {
        return error(errorCode, message, null);
    }

    public static ApiResponse<Void> error(
            ErrorCode errorCode,
            String message,
            List<ApiErrorDetail> errors
    ) {
        return new ApiResponse<>(
                errorCode.name(),
                message,
                null,
                errors,
                RequestIdContext.current(),
                Instant.now()
        );
    }
}
