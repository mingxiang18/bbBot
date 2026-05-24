package com.bb.bot.handler.horseRace.engine;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.bb.bot.common.util.ResourcesUtils;
import com.bb.bot.handler.horseRace.entity.HorseRaceEvent;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * 启动时加载 {@code static/horseRace/horseRaceEvent.json}，避免每帧 tick 都重新读盘 + 解析。
 *
 * @author ren
 */
@Slf4j
@Component
public class HorseRaceEventLoader {

    @Autowired
    private ResourcesUtils resourcesUtils;

    private List<HorseRaceEvent> events = Collections.emptyList();

    @PostConstruct
    public void load() {
        try {
            byte[] raw = resourcesUtils.getStaticResourceToByte("horseRace/horseRaceEvent.json");
            events = JSON.parseObject(new String(raw, StandardCharsets.UTF_8),
                    new TypeReference<List<HorseRaceEvent>>() {});
            if (events == null) {
                events = Collections.emptyList();
            }
        } catch (Exception e) {
            log.warn("Failed to load horse race events, falling back to empty list", e);
            events = Collections.emptyList();
        }
    }

    public List<HorseRaceEvent> getEvents() {
        return events;
    }
}
