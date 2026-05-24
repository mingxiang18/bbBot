package com.bb.bot.database.aiAgent.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bb.bot.database.aiAgent.entity.AiSkill;
import com.bb.bot.database.aiAgent.mapper.AiSkillMapper;
import com.bb.bot.database.aiAgent.service.IAiSkillService;
import org.springframework.stereotype.Service;

@Service
public class AiSkillServiceImpl extends ServiceImpl<AiSkillMapper, AiSkill> implements IAiSkillService {
}
