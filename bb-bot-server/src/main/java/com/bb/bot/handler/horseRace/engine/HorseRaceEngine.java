package com.bb.bot.handler.horseRace.engine;

import com.bb.bot.handler.horseRace.entity.Horse;
import com.bb.bot.handler.horseRace.entity.HorseRaceEvent;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

/**
 * 赛马规则引擎。所有外部依赖通过参数 / 构造器注入：
 * 随机源 + 事件列表 + 配置参数。{@link HorseRaceState} 是数据载体。
 *
 * <p>核心方法 {@link #tick(HorseRaceState, List)} 是纯函数式（除原地修改 state），
 * 给定相同 seed 的 {@link RandomGenerator}、相同 state、相同 events，输出确定。
 *
 * @author ren
 */
public class HorseRaceEngine {

    public static final int DEFAULT_INITIAL_HORSE_COUNT = 7;
    public static final int DEFAULT_HORSE_MOVE_LENGTH = 2;
    public static final int DEFAULT_SLIDE_LENGTH = 18;
    public static final int DEFAULT_GLOBAL_EVENT_RATE = 50;
    public static final int DEFAULT_INDEPENDENT_EVENT_RATE = 80;
    public static final String DEFAULT_HORSE_CHAR = "🐎";
    public static final char SLIDE_CHAR = 'Ξ';

    private static final String[] HORSE_NAMES = {
            "闪电", "风驰", "骁勇", "赤焰", "银箭", "黑影", "星辰", "迅捷", "雷霆", "白雪",
            "奔雷", "赤兔", "飞羽", "火焰", "金星", "夜影", "流星", "烈风", "苍穹", "霜雪"
    };
    private static final String[] HORSE_TRAITS = {
            "速度极快", "耐力出众", "灵活敏捷", "体型健硕", "善于冲刺", "持久力强", "反应迅速", "勇猛无畏",
            "稳重可靠", "意志坚定", "聪明机智", "力量惊人", "跳跃能力强", "步伐轻盈", "爆发力强", "适应力强",
            "胆识过人", "心态平稳", "精力充沛", "热情洋溢"
    };
    private static final String[] HORSE_PERFORMANCE = {
            "在比赛中总是领先", "在关键时刻表现优异", "经常赢得比赛", "以稳健著称", "爆发力十足", "常常超越对手",
            "具备卓越的竞赛能力", "表现极为稳定", "以冷静闻名", "能在任何条件下表现出色", "在赛场上非常耀眼",
            "以绝对优势取胜", "总是充满斗志", "以迅猛的速度著称", "以绝妙的技巧著称", "能在长时间比赛中保持优势",
            "在起跑时表现出色", "以高超的耐力而闻名", "在激烈的竞争中脱颖而出", "经常创造新的纪录"
    };

    private final RandomGenerator random;
    private final int slideLength;
    private final int globalEventRate;
    private final int independentEventRate;

    public HorseRaceEngine() {
        this(new java.util.Random(), DEFAULT_SLIDE_LENGTH, DEFAULT_GLOBAL_EVENT_RATE, DEFAULT_INDEPENDENT_EVENT_RATE);
    }

    public HorseRaceEngine(RandomGenerator random, int slideLength, int globalEventRate, int independentEventRate) {
        this.random = random;
        this.slideLength = slideLength;
        this.globalEventRate = globalEventRate;
        this.independentEventRate = independentEventRate;
    }

    public HorseRaceState newGame() {
        HorseRaceState state = new HorseRaceState();
        for (int i = 0; i < DEFAULT_INITIAL_HORSE_COUNT; i++) {
            state.getHorses().add(buildHorse(i + 1, DEFAULT_HORSE_CHAR));
        }
        return state;
    }

    public void addHorse(HorseRaceState state, String icon) {
        if (state.getRaceState() != 0) {
            throw new IllegalArgumentException("比赛进行中或已结束，无法添加马匹");
        }
        state.getHorses().add(buildHorse(state.getHorses().size() + 1,
                StringUtils.isBlank(icon) ? DEFAULT_HORSE_CHAR : icon));
    }

    public String describeHorses(HorseRaceState state) {
        StringBuilder sb = new StringBuilder();
        for (Horse horse : state.getHorses()) {
            sb.append(horse.getDescription()).append("\n");
        }
        return sb.toString();
    }

    public String renderHorses(HorseRaceState state) {
        StringBuilder out = new StringBuilder();
        for (Horse horse : state.getHorses()) {
            out.append(horse.getSeq()).append(" ");
            for (int j = 0; j < slideLength; j++) {
                out.append(j == horse.getNowPosition() ? horse.getIcon() : SLIDE_CHAR);
            }
            out.append("\n");
        }
        out.append("\n");
        return out.toString();
    }

    /** 推进一帧。返回该帧的事件描述。会就地修改 state（包括 raceState 推进到 1）。 */
    public String tick(HorseRaceState state, List<HorseRaceEvent> events) {
        state.setRaceState(1);
        StringBuilder eventContent = new StringBuilder();

        List<HorseRaceEvent> independentEvents = events.stream()
                .filter(e -> e.getEventType() == 1).collect(Collectors.toList());
        List<HorseRaceEvent> globalEvents = events.stream()
                .filter(e -> e.getEventType() == 2).collect(Collectors.toList());

        HorseRaceEvent globalEvent = null;
        if (!globalEvents.isEmpty() && random.nextInt(100) > globalEventRate) {
            globalEvent = globalEvents.get(random.nextInt(globalEvents.size()));
            eventContent.append(globalEvent.getEventName()).append("\n");
        }

        for (int i = 0; i < state.getHorses().size(); i++) {
            Horse horse = state.getHorses().get(i);
            int slideChange = horse.getMoveLength();
            if (globalEvent != null) {
                slideChange += globalEvent.getEventEffect();
            }

            if (!independentEvents.isEmpty() && random.nextInt(100) > independentEventRate) {
                HorseRaceEvent indep = independentEvents.get(random.nextInt(independentEvents.size()));
                slideChange += indep.getEventEffect();
                eventContent.append(indep.getEventName().replace("{random_horse}", (i + 1) + "号马")).append("\n");
            }

            int next = horse.getNowPosition() + slideChange;
            horse.setNowPosition(Math.max(0, Math.min(slideLength - 1, next)));
        }
        return eventContent.toString();
    }

    public boolean isFinished(HorseRaceState state) {
        if (state.getRaceState() == 2) {
            return true;
        }
        for (Horse horse : state.getHorses()) {
            if (horse.getNowPosition() >= slideLength - 1) {
                state.setRaceState(2);
                return true;
            }
        }
        return false;
    }

    public void finish(HorseRaceState state) {
        state.setRaceState(2);
    }

    public String winnerSummary(HorseRaceState state) {
        List<Integer> winners = new ArrayList<>();
        for (int i = 0; i < state.getHorses().size(); i++) {
            if (state.getHorses().get(i).getNowPosition() >= slideLength - 1) {
                winners.add(i);
            }
        }
        return "比赛结束！" + winners.stream().map(idx -> (idx + 1) + "号马")
                .collect(Collectors.joining("、")) + "获胜！";
    }

    private Horse buildHorse(int seq, String icon) {
        return new Horse(seq,
                HORSE_NAMES[random.nextInt(HORSE_NAMES.length)],
                icon,
                String.format("%s是一匹%s的马，%s。",
                        seq + "号位",
                        HORSE_TRAITS[random.nextInt(HORSE_TRAITS.length)],
                        HORSE_PERFORMANCE[random.nextInt(HORSE_PERFORMANCE.length)]),
                DEFAULT_HORSE_MOVE_LENGTH,
                0);
    }
}
