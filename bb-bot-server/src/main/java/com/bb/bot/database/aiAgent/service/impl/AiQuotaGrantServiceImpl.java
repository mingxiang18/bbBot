package com.bb.bot.database.aiAgent.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bb.bot.database.aiAgent.entity.AiQuotaGrant;
import com.bb.bot.database.aiAgent.mapper.AiQuotaGrantMapper;
import com.bb.bot.database.aiAgent.service.IAiQuotaGrantService;
import org.springframework.stereotype.Service;

@Service
public class AiQuotaGrantServiceImpl extends ServiceImpl<AiQuotaGrantMapper, AiQuotaGrant>
        implements IAiQuotaGrantService {
}
