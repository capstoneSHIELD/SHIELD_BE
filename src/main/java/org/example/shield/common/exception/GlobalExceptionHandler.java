package org.example.shield.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.example.shield.common.response.ApiResponse;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        log.warn("Business exception: {}", e.getMessage());
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validation failed: {}", message);
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("Message not readable: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        log.warn("Access denied: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ErrorCode.ACCESS_DENIED.getMessage()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(ObjectOptimisticLockingFailureException e) {
        log.warn("동시성 충돌. entity={}, id={}", e.getPersistentClassName(), e.getIdentifier());
        ErrorCode code = resolveOptimisticLockErrorCode(e.getPersistentClassName());
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.error(code.getMessage()));
    }

    private ErrorCode resolveOptimisticLockErrorCode(String entityName) {
        if (entityName == null) return ErrorCode.CONCURRENT_UPDATE_CONFLICT;
        if (entityName.endsWith(".LawyerProfile")) return ErrorCode.VERIFICATION_CONFLICT;
        if (entityName.endsWith(".Brief"))         return ErrorCode.BRIEF_ALREADY_ACCEPTED;
        if (entityName.endsWith(".Consultation"))  return ErrorCode.CONSULTATION_CONCURRENT_UPDATE;
        return ErrorCode.CONCURRENT_UPDATE_CONFLICT;
    }

    @ExceptionHandler(CannotAcquireLockException.class)
    public ResponseEntity<ApiResponse<Void>> handleDeadlock(CannotAcquireLockException e) {
        log.error("DB 락 획득 실패. {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ErrorCode.CONCURRENT_UPDATE_CONFLICT.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("DB 제약 위반. {}", e.getMessage());
        String msg = e.getMessage() != null ? e.getMessage() : "";
        if (msg.contains("uk_deliveries_brief_confirmed")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(ErrorCode.BRIEF_ALREADY_ACCEPTED.getMessage()));
        }
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }
}
