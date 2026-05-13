package com.bb.bot.handler.horseRace.engine;

import com.bb.bot.handler.horseRace.entity.Horse;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 赛马可序列化状态：仅在赛前初始化阶段持久化（添加马匹、下注），
 * 真正比赛运行时直接消费内存对象，最后清空状态。
 *
 * @author ren
 */
@Data
@NoArgsConstructor
public class HorseRaceState {

    /** 0=未开始 1=进行中 2=结束 */
    private int raceState = 0;

    private List<Horse> horses = new ArrayList<>();
}
