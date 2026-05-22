package com.bb.bot.database.aiAgent.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bb.bot.database.aiAgent.entity.AiModelPricing;
import com.bb.bot.database.aiAgent.mapper.AiModelPricingMapper;
import com.bb.bot.database.aiAgent.service.IAiModelPricingService;
import org.springframework.stereotype.Service;

@Service
public class AiModelPricingServiceImpl extends ServiceImpl<AiModelPricingMapper, AiModelPricing>
        implements IAiModelPricingService {
}
