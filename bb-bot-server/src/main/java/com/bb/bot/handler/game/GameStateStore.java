package com.bb.bot.handler.game;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.database.userConfigInfo.entity.UserConfigValue;
import com.bb.bot.database.userConfigInfo.service.IUserConfigValueService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 游戏状态持久化薄封装。把游戏状态当作一行 {@link UserConfigValue}
 * 存放（type=GAME_SESSION, key_name=gameType, value_name=stateJson），
 * 这样不用新增表也能让进行中的游戏跨重启幸存。
 *
 * <p>同一个 group + gameType 只允许存在一条 active 记录；查询时按 last("limit 1") 兜底。
 *
 * @author ren
 */
@Component
public class GameStateStore {

    public static final String TYPE = "GAME_SESSION";

    @Autowired
    private IUserConfigValueService userConfigValueService;

    /** 读取活动会话；不存在返回 empty。 */
    public Optional<UserConfigValue> findActive(String gameType, String groupId) {
        UserConfigValue v = userConfigValueService.getOne(filter(gameType, groupId));
        return Optional.ofNullable(v);
    }

    /** 写入或更新会话状态。 */
    public void save(String gameType, String groupId, String stateJson) {
        Optional<UserConfigValue> existing = findActive(gameType, groupId);
        UserConfigValue row = existing.orElseGet(UserConfigValue::new);
        row.setType(TYPE);
        row.setGroupId(groupId);
        row.setKeyName(gameType);
        row.setValueName(StringUtils.defaultString(stateJson));
        if (row.getId() == null) {
            userConfigValueService.save(row);
        } else {
            userConfigValueService.updateById(row);
        }
    }

    /** 结束 / 取消会话。 */
    public void clear(String gameType, String groupId) {
        userConfigValueService.remove(filter(gameType, groupId));
    }

    private LambdaQueryWrapper<UserConfigValue> filter(String gameType, String groupId) {
        return new LambdaQueryWrapper<UserConfigValue>()
                .eq(UserConfigValue::getType, TYPE)
                .eq(UserConfigValue::getGroupId, groupId)
                .eq(UserConfigValue::getKeyName, gameType)
                .last("limit 1");
    }
}
