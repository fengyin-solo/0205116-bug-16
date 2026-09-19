package com.redtourism.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redtourism.common.Constants;
import com.redtourism.common.Result;
import com.redtourism.entity.User;
import com.redtourism.service.UserService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Collections;

/**
 * 以 HttpSession 中登录时写入的用户作为唯一认证来源。
 * 每次请求都以数据库中最新的角色和状态为准重建认证上下文：
 * 管理员调整角色或禁用账号后，无论用户从哪个入口访问都会立刻按新权限生效。
 */
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private final UserService userService;

    public SessionAuthenticationFilter(UserService userService) {
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        User sessionUser = session != null
                ? (User) session.getAttribute(Constants.SESSION_USER) : null;

        if (sessionUser != null) {
            User fresh = userService.getById(sessionUser.getId());
            // 账号已删除或已被禁用：立即清除登录态
            if (fresh == null || fresh.getStatus() == null
                    || fresh.getStatus() == Constants.STATUS_DISABLED) {
                session.invalidate();
                SecurityContextHolder.clearContext();
            } else {
                fresh.setPassword(null);
                session.setAttribute(Constants.SESSION_USER, fresh);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                fresh, null,
                                Collections.singletonList(
                                        new SimpleGrantedAuthority("ROLE_" + fresh.getRole())));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    /** 输出统一 JSON 的工具方法，供入口点/拒绝处理器复用 */
    static void writeJson(HttpServletResponse response, int httpStatus, Result<?> body) throws IOException {
        response.setStatus(httpStatus);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        new ObjectMapper().writeValue(response.getWriter(), body);
    }
}
