package com.redtourism.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.redtourism.common.Result;
import com.redtourism.common.SessionUtils;
import com.redtourism.entity.Comment;
import com.redtourism.entity.User;
import com.redtourism.service.InteractionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/comment")
public class CommentController {

    @Autowired
    private InteractionService interactionService;

    @GetMapping("/add")
    public Result<String> add(@RequestParam String targetType,
                               @RequestParam Long targetId,
                               @RequestParam String content,
                               @RequestParam(required = false) String images,
                               @RequestParam(required = false, defaultValue = "5") Integer rating,
                               HttpSession session) {
        User user = SessionUtils.requireUser(session);
        Comment comment = new Comment();
        comment.setUserId(user.getId());
        comment.setTargetType(targetType);
        comment.setTargetId(targetId);
        comment.setContent(content);
        comment.setImages(images);
        comment.setRating(rating);
        interactionService.addComment(comment);
        return Result.success("评论成功", null);
    }

    @GetMapping("/list")
    public Result<IPage<Comment>> list(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "10") int size,
                                        @RequestParam(required = false) String targetType,
                                        @RequestParam(required = false) Long targetId) {
        return Result.success(interactionService.listComments(page, size, targetType, targetId));
    }

    /** 用户删除留言：仅允许删除本人留言；管理员走 /api/admin/comment/delete */
    @GetMapping("/delete")
    public Result<String> delete(@RequestParam Long id, HttpSession session) {
        User user = SessionUtils.requireUser(session);
        Comment comment = interactionService.getCommentById(id);
        if (comment == null) return Result.error("留言不存在");
        if (!comment.getUserId().equals(user.getId())) {
            throw new com.redtourism.common.ForbiddenException("只能删除自己的留言");
        }
        interactionService.deleteComment(id);
        return Result.success("删除成功", null);
    }
}
