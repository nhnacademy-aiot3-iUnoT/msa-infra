package com.nhnacademy.gateway.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    MISSING_TOKEN(
            HttpStatus.UNAUTHORIZED,
            "AU001",
            "토큰이 존재하지 않습니다."
    ),

    INVALID_TOKEN(
            HttpStatus.UNAUTHORIZED,
            "AU002",
            "유효하지 않은 토큰입니다."
    ),

    EXPIRED_TOKEN(
            HttpStatus.UNAUTHORIZED,
            "AU003",
            "만료된 토큰입니다."
    ),

    INTERNAL_SERVER_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "G003",
            "서버 내부 오류가 발생했습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
