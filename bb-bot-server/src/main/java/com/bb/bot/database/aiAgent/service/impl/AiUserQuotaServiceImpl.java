package com.bb.bot.database.aiAgent.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bb.bot.database.aiAgent.entity.AiUserQuota;
import com.bb.bot.database.aiAgent.mapper.AiUserQuotaMapper;
import com.bb.bot.database.aiAgent.service.IAiUserQuotaService;
import org.springframework.stereotype.Service;

@Service
public class AiUserQuotaServiceImpl extends ServiceImpl<AiUserQuotaMapper, AiUserQuota>
        implements IAiUserQuotaService {
}
