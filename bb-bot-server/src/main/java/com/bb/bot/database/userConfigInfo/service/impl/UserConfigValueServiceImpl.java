package com.bb.bot.database.userConfigInfo.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.bb.bot.database.userConfigInfo.mapper.UserConfigValueMapper;
import com.bb.bot.database.userConfigInfo.entity.UserConfigValue;
import com.bb.bot.database.userConfigInfo.service.IUserConfigValueService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
 * 用户相关数据Service业务层处理
 *
 * @author rym
 * @date 2024-01-29
 */
@Service
public class UserConfigValueServiceImpl extends ServiceImpl<UserConfigValueMapper,UserConfigValue> implements IUserConfigValueService {
    @Autowired
    private UserConfigValueMapper userConfigValueMapper;


}
