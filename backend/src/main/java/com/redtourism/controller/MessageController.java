package com.redtourism.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.redtourism.common.Constants;
import com.redtourism.common.Result;
import com.redtourism.common.SessionUtils;
import com.redtourism.entity.Message;
import com.redtourism.entity.User;
import com.redtourism.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/message")
public class MessageController {

    @Autowired
    private MessageService messageService;

    @GetMapping("/list")
    public Result<IPage<Message>> list(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "10") int size,
                                        @RequestParam(required = false) Integer isRead,
                                        HttpSession session) {
        User user = SessionUtils.requireUser(session);
        return Result.success(messageService.listMessages(page, size, user.getId(), isRead));
    }

    /** 只能标记属于当前登录用户自己的消息，防止越权改动他人数据 */
    @GetMapping("/read")
    public Result<String> markRead(@RequestParam Long id, HttpSession session) {
        User user = SessionUtils.requireUser(session);
        boolean ok = messageService.markRead(id, user.getId());
        if (!ok) return Result.error("消息不存在或无权操作");
        return Result.success("已读", null);
    }

    @GetMapping("/readAll")
    public Result<String> markAllRead(HttpSession session) {
        User user = SessionUtils.requireUser(session);
        messageService.markAllRead(user.getId());
        return Result.success("全部已读", null);
    }

    @GetMapping("/unreadCount")
    public Result<Long> unreadCount(HttpSession session) {
        User user = (User) session.getAttribute(Constants.SESSION_USER);
        if (user == null) return Result.success(0L);
        return Result.success(messageService.countUnread(user.getId()));
    }
}
