package com.bb.bot.database.aiAgent.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bb.bot.database.aiAgent.entity.AiToolPolicy;
import com.bb.bot.database.aiAgent.mapper.AiToolPolicyMapper;
import com.bb.bot.database.aiAgent.service.IAiToolPolicyService;
import org.springframework.stereotype.Service;

@Service
public class AiToolPolicyServiceImpl extends ServiceImpl<AiToolPolicyMapper, AiToolPolicy> implements IAiToolPolicyService {
}
