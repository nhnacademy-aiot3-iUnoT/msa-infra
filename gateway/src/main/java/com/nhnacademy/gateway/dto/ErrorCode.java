package com.nhnacademy.gateway.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    UNAUTHORIZED(
            HttpStatus.UNAUTHORIZED,
            "AU002",
            "인증이 필요합니다."
    ),

    INVALID_TOKEN(
            HttpStatus.UNAUTHORIZED,
            "AU003",
            "유효하지 않은 토큰입니다."
    ),

    EXPIRED_TOKEN(
            HttpStatus.UNAUTHORIZED,
            "AU004",
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
