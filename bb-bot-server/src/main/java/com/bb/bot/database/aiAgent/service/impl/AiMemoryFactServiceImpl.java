package com.bb.bot.database.aiAgent.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bb.bot.database.aiAgent.entity.AiMemoryFact;
import com.bb.bot.database.aiAgent.mapper.AiMemoryFactMapper;
import com.bb.bot.database.aiAgent.service.IAiMemoryFactService;
import org.springframework.stereotype.Service;

@Service
public class AiMemoryFactServiceImpl extends ServiceImpl<AiMemoryFactMapper, AiMemoryFact> implements IAiMemoryFactService {
}
