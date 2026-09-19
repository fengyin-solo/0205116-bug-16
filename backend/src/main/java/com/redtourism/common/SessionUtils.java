package com.redtourism.common;

import com.redtourism.entity.User;

import javax.servlet.http.HttpSession;

/**
 * 会话相关工具：统一获取当前登录用户、管理员。
 * 鉴权入口只有一个（Spring Security + 本工具），避免不同控制器各写一套导致绕过。
 */
public final class SessionUtils {

    private SessionUtils() {}

    /** 获取当前登录用户，未登录返回 null */
    public static User currentUser(HttpSession session) {
        if (session == null) return null;
        return (User) session.getAttribute(Constants.SESSION_USER);
    }

    /** 仅当登录账号为 ADMIN 时返回该用户，否则返回 null */
    public static User currentAdmin(HttpSession session) {
        User user = currentUser(session);
        if (user != null && Constants.ROLE_ADMIN.equals(user.getRole())) {
            return user;
        }
        return null;
    }

    /** ADMIN 或 STAFF（工作人员只能操作归属自己的资源，具体资源由控制器再校验） */
    public static User currentStaffOrAdmin(HttpSession session) {
        User user = currentUser(session);
        if (user != null && (Constants.ROLE_ADMIN.equals(user.getRole())
                || Constants.ROLE_STAFF.equals(user.getRole()))) {
            return user;
        }
        return null;
    }

    /** 操作人描述，用于审计日志 */
    public static String operatorDesc(User user) {
        if (user == null) return "未知用户";
        return user.getId() + ":" + user.getUsername() + "(" + user.getRole() + ")";
    }

    /** 要求必须为管理员，否则抛出带明确提示的异常（供控制器纵深防御使用） */
    public static User requireAdmin(HttpSession session) {
        User user = currentUser(session);
        if (user == null) {
            throw new NotLoginException("未登录或登录已过期，请先登录");
        }
        if (!Constants.ROLE_ADMIN.equals(user.getRole())) {
            throw new ForbiddenException("权限不足：仅管理员可执行该操作");
        }
        return user;
    }

    /** 要求为管理员或工作人员 */
    public static User requireStaffOrAdmin(HttpSession session) {
        User user = currentUser(session);
        if (user == null) {
            throw new NotLoginException("未登录或登录已过期，请先登录");
        }
        if (!Constants.ROLE_ADMIN.equals(user.getRole())
                && !Constants.ROLE_STAFF.equals(user.getRole())) {
            throw new ForbiddenException("权限不足：仅管理员或工作人员可执行该操作");
        }
        return user;
    }

    /** 要求为普通登录用户（不限角色） */
    public static User requireUser(HttpSession session) {
        User user = currentUser(session);
        if (user == null) {
            throw new NotLoginException("未登录或登录已过期，请先登录");
        }
        return user;
    }
}
