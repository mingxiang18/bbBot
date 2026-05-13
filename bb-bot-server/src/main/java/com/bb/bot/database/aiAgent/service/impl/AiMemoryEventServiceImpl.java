package com.bb.bot.database.aiAgent.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bb.bot.database.aiAgent.entity.AiMemoryEvent;
import com.bb.bot.database.aiAgent.mapper.AiMemoryEventMapper;
import com.bb.bot.database.aiAgent.service.IAiMemoryEventService;
import org.springframework.stereotype.Service;

@Service
public class AiMemoryEventServiceImpl extends ServiceImpl<AiMemoryEventMapper, AiMemoryEvent> implements IAiMemoryEventService {
}
