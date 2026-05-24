package com.bb.bot.database.aiAgent.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bb.bot.database.aiAgent.entity.AiCronTask;
import com.bb.bot.database.aiAgent.mapper.AiCronTaskMapper;
import com.bb.bot.database.aiAgent.service.IAiCronTaskService;
import org.springframework.stereotype.Service;

@Service
public class AiCronTaskServiceImpl extends ServiceImpl<AiCronTaskMapper, AiCronTask> implements IAiCronTaskService {
}
