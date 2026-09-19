package com.redtourism.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.redtourism.common.ForbiddenException;
import com.redtourism.common.Result;
import com.redtourism.common.SessionUtils;
import com.redtourism.entity.SysRole;
import com.redtourism.entity.SysRoleMenu;
import com.redtourism.entity.User;
import com.redtourism.mapper.SysRoleMapper;
import com.redtourism.mapper.SysRoleMenuMapper;
import com.redtourism.service.AuditLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.List;

/**
 * 角色与菜单权限配置。该模块属于系统级管理功能，仅允许 ADMIN 访问，
 * 工作人员与普通用户无论从哪个入口都不能改动。
 */
@RestController
@RequestMapping("/api/admin/role")
public class RolePermissionController {

    @Autowired private SysRoleMapper roleMapper;
    @Autowired private SysRoleMenuMapper menuMapper;
    @Autowired private AuditLogService auditLogService;

    @GetMapping("/list")
    public Result<List<SysRole>> listRoles(HttpSession session) {
        requireAdmin(session);
        return Result.success(roleMapper.selectList(null));
    }

    @GetMapping("/save")
    public Result<String> saveRole(@RequestParam(required = false) Long id,
                                    @RequestParam String code,
                                    @RequestParam String name,
                                    @RequestParam(required = false) String description,
                                    HttpSession session, HttpServletRequest request) {
        User admin = requireAdmin(session);
        SysRole role = id != null ? roleMapper.selectById(id) : new SysRole();
        if (role == null) role = new SysRole();
        role.setCode(code);
        role.setName(name);
        role.setDescription(description);
        if (id != null) { role.setId(id); roleMapper.updateById(role); }
        else roleMapper.insert(role);
        audit(admin, "ROLE", "SAVE", "roleId=" + id,
                (id == null ? "新增角色 " : "修改角色 ") + code, request);
        return Result.success("保存成功", null);
    }

    @GetMapping("/delete")
    public Result<String> deleteRole(@RequestParam Long id,
                                     HttpSession session, HttpServletRequest request) {
        User admin = requireAdmin(session);
        SysRole role = roleMapper.selectById(id);
        if (role == null) return Result.error("角色不存在");
        // 内置角色不允许删除，避免锁死系统
        if ("ADMIN".equals(role.getCode()) || "USER".equals(role.getCode()) || "STAFF".equals(role.getCode())) {
            return Result.error("内置角色不允许删除");
        }
        menuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleCode, role.getCode()));
        roleMapper.deleteById(id);
        audit(admin, "ROLE", "DELETE", "roleId=" + id, "删除角色 " + role.getCode(), request);
        return Result.success("删除成功", null);
    }

    @GetMapping("/menu/list")
    public Result<List<SysRoleMenu>> listMenus(@RequestParam String roleCode, HttpSession session) {
        User user = SessionUtils.requireUser(session);
        // 工作人员只能读取本人角色的菜单可见性；查看/修改其他角色一律仅 ADMIN
        if (!"ADMIN".equals(user.getRole()) && !roleCode.equals(user.getRole())) {
            throw new ForbiddenException("无权查看其他角色权限");
        }
        return Result.success(menuMapper.selectList(
                new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleCode, roleCode)));
    }

    @GetMapping("/menu/save")
    public Result<String> saveMenu(@RequestParam(required = false) Long id,
                                    @RequestParam String roleCode,
                                    @RequestParam String menuKey,
                                    @RequestParam(required = false) String menuName,
                                    @RequestParam(required = false, defaultValue = "1") Integer enabled,
                                    HttpSession session, HttpServletRequest request) {
        User admin = requireAdmin(session);
        SysRoleMenu m = id != null ? menuMapper.selectById(id) : new SysRoleMenu();
        if (m == null) m = new SysRoleMenu();
        m.setRoleCode(roleCode);
        m.setMenuKey(menuKey);
        m.setMenuName(menuName);
        m.setEnabled(enabled);
        if (id != null) menuMapper.updateById(m);
        else menuMapper.insert(m);
        audit(admin, "ROLE", "SAVE_MENU", "menuId=" + id,
                "角色 " + roleCode + " 菜单 " + menuKey + " enabled=" + enabled, request);
        return Result.success("保存成功", null);
    }

    @GetMapping("/menu/toggle")
    public Result<String> toggleMenu(@RequestParam Long id,
                                     HttpSession session, HttpServletRequest request) {
        User admin = requireAdmin(session);
        SysRoleMenu m = menuMapper.selectById(id);
        if (m == null) return Result.error("权限项不存在");
        m.setEnabled(m.getEnabled() == 1 ? 0 : 1);
        menuMapper.updateById(m);
        audit(admin, "ROLE", "TOGGLE_MENU", "menuId=" + id,
                "角色 " + m.getRoleCode() + " 菜单 " + m.getMenuKey() + " -> " + m.getEnabled(), request);
        return Result.success("已切换", null);
    }

    @GetMapping("/menu/delete")
    public Result<String> deleteMenu(@RequestParam Long id,
                                     HttpSession session, HttpServletRequest request) {
        User admin = requireAdmin(session);
        SysRoleMenu m = menuMapper.selectById(id);
        if (m == null) return Result.error("权限项不存在");
        menuMapper.deleteById(id);
        audit(admin, "ROLE", "DELETE_MENU", "menuId=" + id,
                "角色 " + m.getRoleCode() + " 菜单 " + m.getMenuKey(), request);
        return Result.success("删除成功", null);
    }

    private User requireAdmin(HttpSession session) {
        return SessionUtils.requireAdmin(session);
    }

    private void audit(User admin, String module, String action, String target,
                       String detail, HttpServletRequest request) {
        auditLogService.log(admin, module, action, target, "SUCCESS", detail, request);
    }
}
