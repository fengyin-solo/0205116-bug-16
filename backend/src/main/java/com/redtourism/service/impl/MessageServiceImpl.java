package com.redtourism.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.redtourism.entity.Message;
import com.redtourism.mapper.MessageMapper;
import com.redtourism.service.MessageService;
import org.springframework.stereotype.Service;

@Service
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message> implements MessageService {

    @Override
    public IPage<Message> listMessages(int page, int size, Long userId, Integer isRead) {
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getUserId, userId);
        if (isRead != null) {
            wrapper.eq(Message::getIsRead, isRead);
        }
        wrapper.orderByDesc(Message::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    public boolean markRead(Long id, Long userId) {
        Message msg = getById(id);
        if (msg == null || !msg.getUserId().equals(userId)) return false;
        if (msg.getIsRead() != null && msg.getIsRead() == 1) return true;
        msg.setIsRead(1);
        return updateById(msg);
    }

    @Override
    public boolean markAllRead(Long userId) {
        return lambdaUpdate()
                .eq(Message::getUserId, userId)
                .eq(Message::getIsRead, 0)
                .set(Message::getIsRead, 1)
                .update();
    }

    @Override
    public long countUnread(Long userId) {
        return lambdaQuery()
                .eq(Message::getUserId, userId)
                .eq(Message::getIsRead, 0)
                .count();
    }

    @Override
    public boolean sendMessage(Long userId, String title, String content) {
        Message msg = new Message();
        msg.setUserId(userId);
        msg.setTitle(title);
        msg.setContent(content);
        msg.setIsRead(0);
        return save(msg);
    }
}
