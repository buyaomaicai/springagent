package com.springagent.common.api;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    OK(HttpStatus.OK, "success", false),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "请求参数不合法", false),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "请求体格式错误", false),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "请求的资源不存在", false),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "请求方法不受支持", false),
    CONFLICT(HttpStatus.CONFLICT, "资源状态冲突", false),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "请求媒体类型不受支持", false),
    NOT_IMPLEMENTED(HttpStatus.NOT_IMPLEMENTED, "接口尚未实现", false),
    DIAGNOSIS_STREAM_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "诊断生成失败", true),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误", false);

    private final HttpStatus httpStatus;
    private final String defaultMessage;
    private final boolean retryable;

    ErrorCode(HttpStatus httpStatus, String defaultMessage, boolean retryable) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
        this.retryable = retryable;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    public boolean retryable() {
        return retryable;
    }
}
