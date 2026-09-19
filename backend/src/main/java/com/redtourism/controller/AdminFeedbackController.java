package com.redtourism.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.redtourism.common.Result;
import com.redtourism.common.SessionUtils;
import com.redtourism.entity.Feedback;
import com.redtourism.entity.User;
import com.redtourism.mapper.FeedbackMapper;
import com.redtourism.mapper.UserMapper;
import com.redtourism.service.AuditLogService;
import com.redtourism.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.Date;

/**
 * 问题反馈的管理端处理。路径 /api/admin/feedback/** 由 SecurityConfig 统一限制为 ADMIN，
 * 不再在 /api/feedback 下暴露管理入口，避免访客或普通用户直接调用。
 */
@RestController
@RequestMapping("/api/admin/feedback")
public class AdminFeedbackController {

    @Autowired private FeedbackMapper feedbackMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private MessageService messageService;
    @Autowired private AuditLogService auditLogService;

    @GetMapping("/list")
    public Result<IPage<Feedback>> adminList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            HttpSession session) {
        SessionUtils.requireAdmin(session);
        LambdaQueryWrapper<Feedback> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) w.eq(Feedback::getStatus, status);
        if (StringUtils.hasText(category)) w.eq(Feedback::getCategory, category);
        if (StringUtils.hasText(keyword))
            w.and(q -> q.like(Feedback::getTitle, keyword).or().like(Feedback::getContent, keyword));
        w.orderByDesc(Feedback::getCreateTime);
        IPage<Feedback> result = feedbackMapper.selectPage(new Page<>(page, size), w);
        result.getRecords().forEach(fb -> {
            if (fb.getUserId() != null) {
                User u = userMapper.selectById(fb.getUserId());
                if (u != null) fb.setUsername(u.getNickname() != null ? u.getNickname() : u.getUsername());
            }
        });
        return Result.success(result);
    }

    @GetMapping("/reply")
    public Result<String> reply(@RequestParam Long id,
                                 @RequestParam String reply,
                                 @RequestParam(defaultValue = "RESOLVED") String status,
                                 HttpSession httpSession, HttpServletRequest request) {
        User admin = SessionUtils.requireAdmin(httpSession);
        Feedback fb = feedbackMapper.selectById(id);
        if (fb == null) return Result.error("反馈不存在");
        fb.setReply(reply);
        fb.setStatus(status);
        fb.setReplyTime(new Date());
        feedbackMapper.updateById(fb);
        if (fb.getUserId() != null) {
            messageService.sendMessage(fb.getUserId(), "您的反馈已收到回复",
                    "您提交的【" + fb.getTitle() + "】已收到回复：" + reply);
        }
        auditLogService.log(admin, "FEEDBACK", "REPLY", "feedbackId=" + id,
                "SUCCESS", "回复反馈【" + fb.getTitle() + "】，状态=" + status, request);
        return Result.success("回复成功", null);
    }

    @GetMapping("/delete")
    public Result<String> delete(@RequestParam Long id,
                                 HttpSession session, HttpServletRequest request) {
        User admin = SessionUtils.requireAdmin(session);
        Feedback fb = feedbackMapper.selectById(id);
        if (fb == null) return Result.error("反馈不存在");
        feedbackMapper.deleteById(id);
        auditLogService.log(admin, "FEEDBACK", "DELETE", "feedbackId=" + id,
                "SUCCESS", "删除反馈【" + fb.getTitle() + "】", request);
        return Result.success("删除成功", null);
    }

    @GetMapping("/pendingCount")
    public Result<Long> pendingCount(HttpSession session) {
        SessionUtils.requireAdmin(session);
        LambdaQueryWrapper<Feedback> w = new LambdaQueryWrapper<>();
        w.eq(Feedback::getStatus, "PENDING");
        return Result.success(feedbackMapper.selectCount(w));
    }
}
