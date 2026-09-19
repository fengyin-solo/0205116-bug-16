package com.redtourism.common;

/**
 * 未登录或登录已过期时抛出，由 GlobalExceptionHandler 统一转成 401 Result JSON。
 */
public class NotLoginException extends RuntimeException {
    public NotLoginException(String message) {
        super(message);
    }
}
