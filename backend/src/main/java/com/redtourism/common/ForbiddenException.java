package com.redtourism.common;

/**
 * 控制器内部鉴权失败（如会话中不是管理员）时抛出，
 * 由 GlobalExceptionHandler 统一转成 403 Result JSON。
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
