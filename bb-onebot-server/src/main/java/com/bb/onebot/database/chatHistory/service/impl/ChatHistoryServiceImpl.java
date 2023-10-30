package com.bb.onebot.database.chatHistory.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bb.onebot.database.chatHistory.entity.ChatHistory;
import com.bb.onebot.database.chatHistory.mapper.ChatHistoryMapper;
import com.bb.onebot.database.chatHistory.service.IChatHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 聊天消息历史记录Service业务层处理
 *
 * @author rym
 * @date 2023-10-26
 */
@Service
public class ChatHistoryServiceImpl extends ServiceImpl<ChatHistoryMapper, ChatHistory> implements IChatHistoryService {
    @Autowired
    private ChatHistoryMapper chatHistoryMapper;


}
