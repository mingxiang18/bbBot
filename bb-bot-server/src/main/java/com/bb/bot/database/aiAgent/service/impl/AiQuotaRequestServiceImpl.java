package com.bb.bot.database.aiAgent.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bb.bot.database.aiAgent.entity.AiQuotaRequest;
import com.bb.bot.database.aiAgent.mapper.AiQuotaRequestMapper;
import com.bb.bot.database.aiAgent.service.IAiQuotaRequestService;
import org.springframework.stereotype.Service;

@Service
public class AiQuotaRequestServiceImpl extends ServiceImpl<AiQuotaRequestMapper, AiQuotaRequest>
        implements IAiQuotaRequestService {
}
