package com.bb.bot.handler.horseRace.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 赛马小游戏的马属性实体
 * @author ren
 */
@Data
@AllArgsConstructor
public class Horse {
    /**
     * 序号
     */
    private int seq;
    /**
     * 马的姓名
     */
    private String name;
    /**
     * 马的图标
     */
    private String icon;
    /**
     * 马的描述
     */
    private String description;
    /**
     * 马的速度（每次移动距离）
     */
    private int moveLength;
    /**
     * 当前比赛的位置
     */
    private int nowPosition = 0;
}
