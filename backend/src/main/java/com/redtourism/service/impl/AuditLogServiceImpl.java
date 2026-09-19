package com.redtourism.service.impl;

import com.redtourism.entity.AdminAuditLog;
import com.redtourism.entity.User;
import com.redtourism.mapper.AdminAuditLogMapper;
import com.redtourism.service.AuditLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

@Slf4j
@Service
public class AuditLogServiceImpl implements AuditLogService {

    @Autowired
    private AdminAuditLogMapper auditLogMapper;

    @Override
    public void log(User operator, String module, String action, String target,
                    String result, String detail, HttpServletRequest request) {
        try {
            if (request == null) {
                ServletRequestAttributes attrs =
                        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attrs != null) request = attrs.getRequest();
            }
            AdminAuditLog record = new AdminAuditLog();
            if (operator != null) {
                record.setOperatorId(operator.getId());
                record.setOperatorName(operator.getUsername());
                record.setOperatorRole(operator.getRole());
            }
            record.setModule(module);
            record.setAction(action);
            record.setTarget(truncate(target, 500));
            record.setResult(result);
            record.setDetail(truncate(detail, 1000));
            record.setIp(resolveIp(request));
            auditLogMapper.insert(record);
        } catch (Exception e) {
            // 审计失败不应影响业务本身，但要在服务日志里留下痕迹
            log.error("写入审计日志失败: module={}, action={}, target={}", module, action, target, e);
        }
    }

    private String resolveIp(HttpServletRequest request) {
        if (request == null) return null;
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            int comma = ip.indexOf(',');
            return comma > 0 ? ip.substring(0, comma).trim() : ip.trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty()) return ip;
        return request.getRemoteAddr();
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
