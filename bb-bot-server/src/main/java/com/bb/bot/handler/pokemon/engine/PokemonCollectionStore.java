package com.bb.bot.handler.pokemon.engine;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.database.userConfigInfo.entity.UserConfigValue;
import com.bb.bot.database.userConfigInfo.service.IUserConfigValueService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户的宝可梦收藏走 {@code user_config_value (type=POKEMON_COLLECTION, key_name=USER_ID)}，
 * 重启不丢。
 *
 * @author ren
 */
@Slf4j
@Component
public class PokemonCollectionStore {

    public static final String TYPE = "POKEMON_COLLECTION";
    private static final String KEY = "indexes";

    @Autowired
    private IUserConfigValueService userConfigValueService;

    public List<Integer> load(String userId) {
        UserConfigValue row = find(userId);
        if (row == null || row.getValueName() == null || row.getValueName().isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<Integer> parsed = JSON.parseObject(row.getValueName(), new TypeReference<List<Integer>>() {});
            return parsed == null ? new ArrayList<>() : new ArrayList<>(parsed);
        } catch (Exception e) {
            log.warn("Failed to parse pokemon collection for user {}, dropping", userId, e);
            return new ArrayList<>();
        }
    }

    public void save(String userId, List<Integer> indexes) {
        UserConfigValue row = find(userId);
        if (row == null) {
            row = new UserConfigValue();
            row.setUserId(userId);
            row.setType(TYPE);
            row.setKeyName(KEY);
            row.setValueName(JSON.toJSONString(indexes));
            userConfigValueService.save(row);
        } else {
            row.setValueName(JSON.toJSONString(indexes));
            userConfigValueService.updateById(row);
        }
    }

    private UserConfigValue find(String userId) {
        return userConfigValueService.getOne(new LambdaQueryWrapper<UserConfigValue>()
                .eq(UserConfigValue::getType, TYPE)
                .eq(UserConfigValue::getUserId, userId)
                .eq(UserConfigValue::getKeyName, KEY)
                .last("limit 1"));
    }
}
