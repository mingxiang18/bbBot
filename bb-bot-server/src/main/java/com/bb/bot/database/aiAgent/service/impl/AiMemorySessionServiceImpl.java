package com.bb.bot.database.aiAgent.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bb.bot.database.aiAgent.entity.AiMemorySession;
import com.bb.bot.database.aiAgent.mapper.AiMemorySessionMapper;
import com.bb.bot.database.aiAgent.service.IAiMemorySessionService;
import org.springframework.stereotype.Service;

@Service
public class AiMemorySessionServiceImpl extends ServiceImpl<AiMemorySessionMapper, AiMemorySession> implements IAiMemorySessionService {
}
