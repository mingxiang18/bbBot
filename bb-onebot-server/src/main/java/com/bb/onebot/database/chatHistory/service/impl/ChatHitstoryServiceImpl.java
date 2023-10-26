package com.bb.onebot.database.chatHistory.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bb.onebot.database.chatHistory.entity.ChatHitstory;
import com.bb.onebot.database.chatHistory.mapper.ChatHitstoryMapper;
import com.bb.onebot.database.chatHistory.service.IChatHitstoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 聊天消息历史记录Service业务层处理
 *
 * @author rym
 * @date 2023-10-26
 */
@Service
public class ChatHitstoryServiceImpl extends ServiceImpl<ChatHitstoryMapper, ChatHitstory> implements IChatHitstoryService {
    @Autowired
    private ChatHitstoryMapper chatHitstoryMapper;


}
