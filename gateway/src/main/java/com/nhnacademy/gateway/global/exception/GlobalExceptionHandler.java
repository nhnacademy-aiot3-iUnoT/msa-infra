package com.nhnacademy.gateway.global.exception;

import com.nhnacademy.gateway.dto.ApiResponse;
import com.nhnacademy.gateway.dto.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("ERROR 발생: {}", e.getMessage(), e);

        return ResponseEntity.status(500).body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR));
    }
}
