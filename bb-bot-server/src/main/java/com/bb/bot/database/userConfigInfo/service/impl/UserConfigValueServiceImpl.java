package com.bb.bot.database.userConfigInfo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
 * @since 2024-01-29
 */
@Service
public class UserConfigValueServiceImpl extends ServiceImpl<UserConfigValueMapper,UserConfigValue> implements IUserConfigValueService {
    @Autowired
    private UserConfigValueMapper userConfigValueMapper;

    @Override
    public void resetUserConfigValue(UserConfigValue userConfigValue) {
        //先删除原来的设置记录
        userConfigValueMapper.delete(new LambdaQueryWrapper<UserConfigValue>()
                .eq(UserConfigValue::getUserId, userConfigValue.getUserId())
                .eq(UserConfigValue::getType, userConfigValue.getType())
                .eq(UserConfigValue::getKeyName, userConfigValue.getKeyName()));

        //重新设置
        userConfigValueMapper.insert(userConfigValue);
    }
}
