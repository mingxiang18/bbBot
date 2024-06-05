package com.bb.bot.handler.horseRace;

import lombok.Data;

@Data
public class HorseRaceEvent {
    /**
     * 事件id
     */
    private Integer eventId;
    /**
     * 事件名称
     */
    private String eventName;
    /**
     * 事件类型，（1-对单独的马影响，2-对全部马影响）
     */
    private Integer eventType;
    /**
     * 事件对马的移动速度影响（最好是>=-1且<=3）
     */
    private Integer eventEffect;
}
