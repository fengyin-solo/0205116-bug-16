package com.redtourism.config;

import com.redtourism.service.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@SuppressWarnings("deprecation")
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    private final JsonSecurityResponseHandler securityResponseHandler;
    private final UserService userService;

    public SecurityConfig(JsonSecurityResponseHandler securityResponseHandler,
                          UserService userService) {
        this.securityResponseHandler = securityResponseHandler;
        this.userService = userService;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable()
            .cors().and()
            // 无状态会话策略：认证信息只来自登录时写入的 HttpSession，
            // 不使用表单登录/HTTP Basic/记住我等可能产生额外入口的机制
            .sessionManagement()
                .sessionFixation().changeSessionId()
            .and()
            .authorizeRequests()
                .antMatchers("/api/auth/**").permitAll()
                .antMatchers("/uploads/**").permitAll()
                // 工作人员（STAFF）仅可使用仪表盘、自己负责的景点管理，
                // 以及读取本人角色的菜单可见性；用户、订单、留言、角色权限等其余
                // /api/admin/** 接口一律仅 ADMIN
                .antMatchers("/api/admin/stats/dashboard").hasAnyRole("ADMIN", "STAFF")
                .antMatchers("/api/admin/spot/**").hasAnyRole("ADMIN", "STAFF")
                .antMatchers("/api/admin/role/menu/list").hasAnyRole("ADMIN", "STAFF")
                .antMatchers("/api/admin/**").hasRole("ADMIN")
                // 其他路径全部放行，由各控制器/会话校验处理普通用户自身资源
                .anyRequest().permitAll()
            .and()
            .exceptionHandling()
                .authenticationEntryPoint(securityResponseHandler)
                .accessDeniedHandler(securityResponseHandler)
            .and()
            .formLogin().disable()
            .httpBasic().disable()
            .rememberMe().disable()
            .logout().disable()
            .addFilterBefore(new SessionAuthenticationFilter(userService),
                    UsernamePasswordAuthenticationFilter.class);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 与现有数据保持一致（明文口令），登录校验仍走 UserService
        return NoOpPasswordEncoder.getInstance();
    }
}
