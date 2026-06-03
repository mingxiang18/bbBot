package com.bb.bot.database.aiAgent.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bb.bot.database.aiAgent.entity.AiMemoryItem;
import com.bb.bot.database.aiAgent.mapper.AiMemoryItemMapper;
import com.bb.bot.database.aiAgent.service.IAiMemoryItemService;
import org.springframework.stereotype.Service;

@Service
public class AiMemoryItemServiceImpl extends ServiceImpl<AiMemoryItemMapper, AiMemoryItem> implements IAiMemoryItemService {
}
