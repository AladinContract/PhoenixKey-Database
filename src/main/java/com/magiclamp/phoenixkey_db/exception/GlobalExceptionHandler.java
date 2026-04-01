package com.magiclamp.phoenixkey_db.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.magiclamp.phoenixkey_db.common.ApiResponse;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;

/**
 * Global exception handler — bắt mọi exception và trả về ApiResponse.
 *
 * <p>
 * Luồng:
 *
 * <pre>
 * 1. Controller ném AppException(ErrorCode.XXX)
 * 2. GlobalExceptionHandler bắt
 * 3. Trả về ApiResponse với code + message + HTTP status tương ứng
 * </pre>
 *
 * <p>
 * HTTP status chỉ dùng để báo cho API Gateway / load balancer,
 * response body luôn là {@code ApiResponse}.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ──────────────────────────────────────────────────────────────
    // AppException — lỗi nghiệp vụ
    // ──────────────────────────────────────────────────────────────

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException ex) {
        log.warn("[{}] {} — {}", ex.getErrorCode().getCode(), ex.getMessage(), ex.getErrorCode().name());
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .code(ex.getErrorCode().getCode())
                .message(ex.getMessage())
                .build();
        return ResponseEntity
                .status(ex.getErrorCode().getStatus())
                .body(response);
    }

    // ──────────────────────────────────────────────────────────────
    // JPA EntityNotFoundException
    // ──────────────────────────────────────────────────────────────

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleEntityNotFound(EntityNotFoundException ex) {
        log.warn("Entity not found: {}", ex.getMessage());
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .code(ErrorCode.USER_NOT_FOUND.getCode())
                .message("Resource not found")
                .build();
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(response);
    }

    // ──────────────────────────────────────────────────────────────
    // Validation errors (Jakarta Bean Validation)
    // ──────────────────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");

        log.warn("Validation error: {}", message);
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .code(ErrorCode.ENUM_INVALID_VALUE.getCode())
                .message(message)
                .build();
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    // ──────────────────────────────────────────────────────────────
    // IllegalArgumentException
    // ──────────────────────────────────────────────────────────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .code(ErrorCode.ENUM_INVALID_VALUE.getCode())
                .message(ex.getMessage())
                .build();
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    // ──────────────────────────────────────────────────────────────
    // Catch-all — lỗi không mong đợi
    // ──────────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception ex) {
        log.error("Unexpected error", ex);
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .code(ErrorCode.SYSTEM_INTERNAL_ERROR.getCode())
                .message("Internal server error")
                .build();
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }
}
