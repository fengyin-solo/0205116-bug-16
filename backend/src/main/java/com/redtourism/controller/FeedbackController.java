package com.redtourism.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.redtourism.common.Constants;
import com.redtourism.common.Result;
import com.redtourism.entity.Feedback;
import com.redtourism.entity.User;
import com.redtourism.mapper.FeedbackMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.Date;
import java.util.List;

/**
 * 用户侧反馈接口（提交、查看本人反馈）。
 * 管理端处理见 AdminFeedbackController（/api/admin/feedback/**，仅 ADMIN）。
 */
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    @Autowired private FeedbackMapper feedbackMapper;

    /** 提交反馈（可匿名） */
    @GetMapping("/submit")
    public Result<String> submit(@RequestParam String title,
                                  @RequestParam String content,
                                  @RequestParam(defaultValue = "GENERAL") String category,
                                  @RequestParam(required = false) String contact,
                                  HttpSession session) {
        Feedback fb = new Feedback();
        User user = (User) session.getAttribute(Constants.SESSION_USER);
        if (user != null) fb.setUserId(user.getId());
        fb.setTitle(title);
        fb.setContent(content);
        fb.setCategory(category);
        fb.setContact(contact);
        fb.setStatus("PENDING");
        fb.setCreateTime(new Date());
        feedbackMapper.insert(fb);
        return Result.success("反馈已提交，我们会尽快处理", null);
    }

    /** 当前登录用户的历史反馈 */
    @GetMapping("/my")
    public Result<List<Feedback>> myFeedbacks(HttpSession session) {
        User user = (User) session.getAttribute(Constants.SESSION_USER);
        if (user == null) return Result.error(401, "请先登录");
        LambdaQueryWrapper<Feedback> w = new LambdaQueryWrapper<>();
        w.eq(Feedback::getUserId, user.getId()).orderByDesc(Feedback::getCreateTime);
        return Result.success(feedbackMapper.selectList(w));
    }
}
