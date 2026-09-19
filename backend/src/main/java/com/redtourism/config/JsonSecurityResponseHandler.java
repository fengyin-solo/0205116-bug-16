package com.redtourism.config;

import com.redtourism.common.Constants;
import com.redtourism.common.Result;
import com.redtourism.entity.User;
import com.redtourism.service.AuditLogService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 未登录 / 已登录但权限不足时的统一响应：
 * 返回前端约定的 Result JSON（401/403），并把越权尝试写入审计日志。
 */
@Component
public class JsonSecurityResponseHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final AuditLogService auditLogService;

    public JsonSecurityResponseHandler(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        auditDenied(request, null);
        SessionAuthenticationFilter.writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                Result.error(401, "未登录或登录已过期，请先登录"));
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        Object attr = request.getSession(false) != null
                ? request.getSession(false).getAttribute(Constants.SESSION_USER) : null;
        User user = attr instanceof User ? (User) attr : null;
        auditDenied(request, user);
        if (user == null) {
            SessionAuthenticationFilter.writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                    Result.error(401, "未登录或登录已过期，请先登录"));
        } else {
            SessionAuthenticationFilter.writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                    Result.error(403, "权限不足：仅管理员可访问该接口"));
        }
    }

    private void auditDenied(HttpServletRequest request, User user) {
        auditLogService.log(user, "SECURITY", "ACCESS_DENIED",
                request.getMethod() + " " + request.getRequestURI()
                        + (request.getQueryString() != null ? "?" + request.getQueryString() : ""),
                "DENIED", user == null ? "未登录访问被拦截" : "非管理员账号访问被拦截", request);
    }
}
