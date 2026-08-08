package com.springagent.common.exception;

import com.springagent.common.api.ApiErrorDetail;
import com.springagent.common.api.ApiResponse;
import com.springagent.common.api.ErrorCode;
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
