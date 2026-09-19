package com.redtourism.service;

import com.redtourism.entity.AdminAuditLog;
import com.redtourism.entity.User;

import javax.servlet.http.HttpServletRequest;

public interface AuditLogService {

    /**
     * 记录一条管理端操作日志。
     *
     * @param operator 操作人（可能为 null，例如未登录被拦截的请求）
     * @param module   业务模块
     * @param action   动作
     * @param target   操作目标
     * @param result   结果（SUCCESS / DENIED）
     * @param detail   详情
     * @param request  HTTP 请求（用于取 IP，可为 null）
     */
    void log(User operator, String module, String action, String target,
             String result, String detail, HttpServletRequest request);
}
