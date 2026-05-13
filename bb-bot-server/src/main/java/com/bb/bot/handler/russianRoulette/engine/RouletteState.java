package com.bb.bot.handler.russianRoulette.engine;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

/**
 * 俄罗斯轮盘可序列化状态。{@link RussianRouletteEngine} 是无状态的，所有"剩什么子弹"
 * 信息都装在这里，方便 JSON 持久化。
 *
 * @author ren
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouletteState {

    private int chamberSize;
    private int bulletPosition;
    private int currentPosition;
    private Set<String> participants = new HashSet<>();
}
