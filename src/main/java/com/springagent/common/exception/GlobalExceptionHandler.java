package com.springagent.common.exception;

import com.springagent.common.api.ApiErrorDetail;
import com.springagent.common.api.ApiResponse;
import com.springagent.common.api.ErrorCode;
import com.springagent.parser.exception.ProjectArtifactParseException;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException exception
    ) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity
                .status(errorCode.httpStatus())
                .body(ApiResponse.error(errorCode, exception.getMessage()));
    }

    /**
     * 将 Parser 的稳定失败分类转换为对外 HTTP 协议。
     *
     * <p>格式错误、不安全 XML、缺少必要字段等问题都来自用户上传内容，返回 400；文件超过 Parser
     * 限制返回 413；IO_ERROR 通常意味着服务器读取 multipart 临时文件失败，因此按内部错误处理，
     * 并且不把底层异常信息返回给客户端。</p>
     */
    @ExceptionHandler(ProjectArtifactParseException.class)
    public ResponseEntity<ApiResponse<Void>> handleArtifactParseException(
            ProjectArtifactParseException exception
    ) {
        if (exception.getReason()
                == ProjectArtifactParseException.Reason.CONTENT_TOO_LARGE) {
            return ResponseEntity
                    .status(ErrorCode.PROJECT_ARTIFACT_TOO_LARGE.httpStatus())
                    .body(ApiResponse.error(
                            ErrorCode.PROJECT_ARTIFACT_TOO_LARGE,
                            exception.getMessage()
                    ));
        }

        if (exception.getReason()
                == ProjectArtifactParseException.Reason.IO_ERROR) {
            log.error(
                    "Failed to read uploaded project artifact, type={}",
                    exception.getArtifactType(),
                    exception
            );
            return ResponseEntity
                    .status(ErrorCode.INTERNAL_SERVER_ERROR.httpStatus())
                    .body(ApiResponse.error(
                            ErrorCode.INTERNAL_SERVER_ERROR
                    ));
        }

        return ResponseEntity
                .status(ErrorCode.PROJECT_ARTIFACT_INVALID.httpStatus())
                .body(ApiResponse.error(
                        ErrorCode.PROJECT_ARTIFACT_INVALID,
                        exception.getMessage()
                ));
    }

    /**
     * 处理 multipart 请求完全没有提供必需文件字段的情况。
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestPart(
            MissingServletRequestPartException exception
    ) {
        List<ApiErrorDetail> errors = List.of(new ApiErrorDetail(
                exception.getRequestPartName(),
                "缺少必要文件"
        ));
        return ResponseEntity
                .status(ErrorCode.INVALID_REQUEST.httpStatus())
                .body(ApiResponse.error(
                        ErrorCode.INVALID_REQUEST,
                        ErrorCode.INVALID_REQUEST.defaultMessage(),
                        errors
                ));
    }

    /**
     * Spring MVC 可能在请求到达 Controller 之前就拒绝超大 multipart，需要单独保持统一错误结构。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException exception
    ) {
        return ResponseEntity
                .status(ErrorCode.PROJECT_ARTIFACT_TOO_LARGE.httpStatus())
                .body(ApiResponse.error(
                        ErrorCode.PROJECT_ARTIFACT_TOO_LARGE
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException exception
    ) {
        List<ApiErrorDetail> errors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new ApiErrorDetail(
                        error.getField(),
                        error.getDefaultMessage()
                ))
                .toList();

        return ResponseEntity
                .status(ErrorCode.INVALID_REQUEST.httpStatus())
                .body(ApiResponse.error(
                        ErrorCode.INVALID_REQUEST,
                        ErrorCode.INVALID_REQUEST.defaultMessage(),
                        errors
                ));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException exception
    ) {
        List<ApiErrorDetail> errors = exception.getConstraintViolations()
                .stream()
                .map(violation -> new ApiErrorDetail(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()
                ))
                .toList();

        return ResponseEntity
                .status(ErrorCode.INVALID_REQUEST.httpStatus())
                .body(ApiResponse.error(
                        ErrorCode.INVALID_REQUEST,
                        ErrorCode.INVALID_REQUEST.defaultMessage(),
                        errors
                ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableRequest(
            HttpMessageNotReadableException exception
    ) {
        return ResponseEntity
                .status(ErrorCode.MALFORMED_REQUEST.httpStatus())
                .body(ApiResponse.error(ErrorCode.MALFORMED_REQUEST));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(
            MissingServletRequestParameterException exception
    ) {
        List<ApiErrorDetail> errors = List.of(new ApiErrorDetail(
                exception.getParameterName(),
                "缺少必要参数"
        ));
        return ResponseEntity
                .status(ErrorCode.INVALID_REQUEST.httpStatus())
                .body(ApiResponse.error(
                        ErrorCode.INVALID_REQUEST,
                        ErrorCode.INVALID_REQUEST.defaultMessage(),
                        errors
                ));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception
    ) {
        List<ApiErrorDetail> errors = List.of(new ApiErrorDetail(
                exception.getName(),
                "参数类型不正确"
        ));
        return ResponseEntity
                .status(ErrorCode.INVALID_REQUEST.httpStatus())
                .body(ApiResponse.error(
                        ErrorCode.INVALID_REQUEST,
                        ErrorCode.INVALID_REQUEST.defaultMessage(),
                        errors
                ));
    }

    @ExceptionHandler({
            NoResourceFoundException.class,
            NoHandlerFoundException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(
            Exception exception
    ) {
        return ResponseEntity
                .status(ErrorCode.RESOURCE_NOT_FOUND.httpStatus())
                .body(ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception
    ) {
        return ResponseEntity
                .status(ErrorCode.METHOD_NOT_ALLOWED.httpStatus())
                .body(ApiResponse.error(ErrorCode.METHOD_NOT_ALLOWED));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException exception
    ) {
        return ResponseEntity
                .status(ErrorCode.UNSUPPORTED_MEDIA_TYPE.httpStatus())
                .body(ApiResponse.error(ErrorCode.UNSUPPORTED_MEDIA_TYPE));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(
            Exception exception
    ) {
        log.error("Unhandled request exception", exception);
        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.httpStatus())
                .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR));
    }
}
