package com.redtourism.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.redtourism.entity.Message;

public interface MessageService extends IService<Message> {
    IPage<Message> listMessages(int page, int size, Long userId, Integer isRead);
    /** 标记已读；仅当消息属于该用户时才生效 */
    boolean markRead(Long id, Long userId);
    boolean markAllRead(Long userId);
    long countUnread(Long userId);
    boolean sendMessage(Long userId, String title, String content);
}
