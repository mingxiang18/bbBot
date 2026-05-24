package com.bb.bot.database.aiAgent.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bb.bot.database.aiAgent.entity.AiToolInvocationLog;
import com.bb.bot.database.aiAgent.mapper.AiToolInvocationLogMapper;
import com.bb.bot.database.aiAgent.service.IAiToolInvocationLogService;
import org.springframework.stereotype.Service;

@Service
public class AiToolInvocationLogServiceImpl extends ServiceImpl<AiToolInvocationLogMapper, AiToolInvocationLog> implements IAiToolInvocationLogService {
}
