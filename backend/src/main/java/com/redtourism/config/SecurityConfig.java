package com.redtourism.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redtourism.common.Result;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
@SuppressWarnings("deprecation")
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable()
            .cors().and()
            .authorizeRequests()
                .antMatchers("/api/auth/**").permitAll()
                .antMatchers("/uploads/**").permitAll()
                .antMatchers("/api/admin/stats/dashboard").hasAnyRole("ADMIN", "STAFF")
                .antMatchers("/api/admin/spot/**").hasAnyRole("ADMIN", "STAFF")
                .antMatchers("/api/admin/role/menu/list").hasAnyRole("ADMIN", "STAFF")
                .antMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().permitAll()
            .and()
            .exceptionHandling()
                .authenticationEntryPoint(jsonAuthenticationEntryPoint())
                .accessDeniedHandler(jsonAccessDeniedHandler())
            .and()
            .formLogin().disable()
            .httpBasic().disable()
            .sessionManagement()
                .maximumSessions(5)
            .and().and()
            .rememberMe()
                .key("red-tourism-remember-me")
                .tokenValiditySeconds(604800);
    }

    /** 未登录访问受保护接口：返回明确的 401 提示（统一 Result 格式） */
    @Bean
    public AuthenticationEntryPoint jsonAuthenticationEntryPoint() {
        return (request, response, authException) ->
                writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, Result.error(401, "请先登录"));
    }

    /** 已登录但角色不符：返回明确的 403 提示（统一 Result 格式） */
    @Bean
    public AccessDeniedHandler jsonAccessDeniedHandler() {
        return (request, response, accessDeniedException) ->
                writeJson(response, HttpServletResponse.SC_FORBIDDEN, Result.error(403, "无权限访问，仅管理员可执行此操作"));
    }

    private void writeJson(HttpServletResponse response, int status, Result<?> body) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(body));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return NoOpPasswordEncoder.getInstance();
    }
}
