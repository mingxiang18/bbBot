package com.bb.bot.handler.russianRoulette.engine;

import java.util.HashSet;
import java.util.random.RandomGenerator;

/**
 * 纯逻辑：把俄罗斯轮盘的规则从 IO 解耦，便于直接 unit-test。
 * Random 注入式以便测试用确定性 seed。
 *
 * @author ren
 */
public class RussianRouletteEngine {

    private final RandomGenerator random;

    public RussianRouletteEngine() {
        this(new java.util.Random());
    }

    public RussianRouletteEngine(RandomGenerator random) {
        this.random = random;
    }

    /** 创建新的一局。{@code chamberSize} 必须 ≥ 2。 */
    public RouletteState newGame(int chamberSize) {
        if (chamberSize < 2) {
            throw new IllegalArgumentException("弹匣容量至少为 2");
        }
        return new RouletteState(
                chamberSize,
                random.nextInt(chamberSize),
                0,
                new HashSet<>());
    }

    public void join(RouletteState state, String userId) {
        state.getParticipants().add(userId);
    }

    public void spin(RouletteState state, String userId) {
        ensureParticipant(state, userId);
        state.setCurrentPosition(random.nextInt(state.getChamberSize()));
    }

    /** 扣下扳机，返回是否击中（true=死）。原地推进 currentPosition。 */
    public boolean pullTrigger(RouletteState state, String userId) {
        ensureParticipant(state, userId);
        boolean hit = state.getCurrentPosition() == state.getBulletPosition();
        state.setCurrentPosition((state.getCurrentPosition() + 1) % state.getChamberSize());
        return hit;
    }

    private void ensureParticipant(RouletteState state, String userId) {
        if (!state.getParticipants().contains(userId)) {
            throw new IllegalArgumentException("你还没有参与进游戏呢！请发送\"参与俄罗斯转盘\"进行参与噢");
        }
    }
}
