package com.bb.bot.database.aiAgent.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bb.bot.database.aiAgent.entity.AiUserRole;
import com.bb.bot.database.aiAgent.mapper.AiUserRoleMapper;
import com.bb.bot.database.aiAgent.service.IAiUserRoleService;
import org.springframework.stereotype.Service;

@Service
public class AiUserRoleServiceImpl extends ServiceImpl<AiUserRoleMapper, AiUserRole> implements IAiUserRoleService {
}
