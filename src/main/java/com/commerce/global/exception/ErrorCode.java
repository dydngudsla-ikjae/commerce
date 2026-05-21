package com.commerce.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    AUTH_INVALID(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다."),
    MEMBER_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 존재하는 이메일입니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 카테고리입니다."),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 상품입니다."),
    VARIANT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 Variant입니다."),
    VARIANT_SOLD_OUT(HttpStatus.BAD_REQUEST, "주문 불가 상태의 상품입니다."),
    DUPLICATE_VARIANT(HttpStatus.CONFLICT, "이미 존재하는 옵션 조합입니다."),
    OPTION_DUPLICATE(HttpStatus.CONFLICT, "이미 존재하는 옵션명입니다."),
    OPTION_VALUE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 옵션값입니다."),

    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 주문입니다."),
    ORDER_INVALID_STATUS(HttpStatus.BAD_REQUEST, "현재 상태에서 허용되지 않는 요청입니다."),
    ORDER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "타인의 주문에 접근할 수 없습니다."),
    OUT_OF_STOCK(HttpStatus.BAD_REQUEST, "재고가 부족합니다."),

    CONCURRENT_CONFLICT(HttpStatus.CONFLICT, "다른 요청이 먼저 처리되었습니다. 다시 시도해 주세요."),

    PAYMENT_GATEWAY_ERROR(HttpStatus.BAD_GATEWAY, "결제 게이트웨이 오류가 발생했습니다."),
    PAYMENT_VERIFY_REQUIRED(HttpStatus.BAD_REQUEST, "결제 검증이 필요합니다."),
    PAYMENT_VERIFY_MISMATCH(HttpStatus.BAD_REQUEST, "결제 검증 상태가 일치하지 않습니다."),
    REFUND_GATEWAY_ERROR(HttpStatus.BAD_GATEWAY, "환불 처리 중 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;
}