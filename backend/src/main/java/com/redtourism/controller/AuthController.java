package com.redtourism.controller;

import com.redtourism.common.Constants;
import com.redtourism.common.Result;
import com.redtourism.entity.User;
import com.redtourism.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.Collections;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @GetMapping("/login")
    public Result<User> login(@RequestParam String username,
                               @RequestParam String password,
                               HttpServletRequest request) {
        User user = userService.login(username, password);
        HttpSession session = request.getSession(false);
        if (session != null) {
            // 登录后更换会话 ID，防止会话固定攻击
            request.changeSessionId();
        } else {
            session = request.getSession(true);
        }
        // 会话中不落密码
        user.setPassword(null);
        session.setAttribute(Constants.SESSION_USER, user);
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(user, null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
        SecurityContextHolder.getContext().setAuthentication(authToken);
        return Result.success("登录成功", user);
    }

    @GetMapping("/register")
    public Result<User> register(@RequestParam String username,
                                  @RequestParam String password,
                                  @RequestParam String phone) {
        User user = userService.register(username, password, phone);
        user.setPassword(null);
        return Result.success("注册成功", user);
    }

    @GetMapping("/resetPassword")
    public Result<String> resetPassword(@RequestParam String phone,
                                         @RequestParam String oldPassword,
                                         @RequestParam String newPassword) {
        userService.resetPassword(phone, oldPassword, newPassword);
        return Result.success("密码重置成功", null);
    }

    @GetMapping("/logout")
    public Result<String> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return Result.success("退出成功", null);
    }

    @GetMapping("/currentUser")
    public Result<User> currentUser(HttpSession session) {
        User user = (User) session.getAttribute(Constants.SESSION_USER);
        if (user == null) {
            return Result.error(401, "未登录");
        }
        User fresh = userService.getById(user.getId());
        if (fresh == null || fresh.getStatus() == Constants.STATUS_DISABLED) {
            session.invalidate();
            SecurityContextHolder.clearContext();
            return Result.error(401, "账号不存在或已被禁用");
        }
        fresh.setPassword(null);
        // 角色可能已被管理员调整，刷新会话中的用户快照，使权限改动对各入口即时生效
        session.setAttribute(Constants.SESSION_USER, fresh);
        return Result.success(fresh);
    }
}
