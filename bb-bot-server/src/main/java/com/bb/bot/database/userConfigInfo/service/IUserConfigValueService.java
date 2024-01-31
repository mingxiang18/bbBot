package com.bb.bot.database.userConfigInfo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bb.bot.database.userConfigInfo.entity.UserConfigValue;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户相关数据Service接口
 * 
 * @author rym
 * @date 2024-01-29
 */
public interface IUserConfigValueService extends IService<UserConfigValue> {

    /**
     * 重新设置指定用户配置的值
     */
    @Transactional(rollbackFor = Exception.class)
    void resetUserConfigValue(UserConfigValue userConfigValue);

}
