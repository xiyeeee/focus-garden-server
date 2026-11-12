package com.xiye.garden.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.ErrorResponse;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> notFound(ResourceNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> validation(BindException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + "：" + (error.isBindingFailure() ? "参数类型或枚举值不正确" : error.getDefaultMessage()))
                .sorted().distinct().collect(java.util.stream.Collectors.joining("；"));
        return error(HttpStatus.BAD_REQUEST, message.isEmpty() ? "请求参数不正确" : message);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            HandlerMethodValidationException.class})
    public ResponseEntity<ApiResponse<Void>> invalidRequest(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "请求格式或参数值不正确；状态仅支持 TODO、IN_PROGRESS、COMPLETED，ID 必须为正整数");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> missingRoute() {
        return error(HttpStatus.NOT_FOUND, "请求的接口不存在");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> methodNotAllowed() {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "请求方法不支持");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> unsupportedMedia() {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "请求体请使用 application/json");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception exception) {
        if (exception instanceof ErrorResponse response) {
            HttpStatus status = HttpStatus.valueOf(response.getStatusCode().value());
            return error(status, "请求无法处理，请检查请求方法、参数和响应格式");
        }
        log.error("请求处理失败", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "服务暂时不可用，请稍后重试");
    }

    private ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(status.name(), message));
    }
}
