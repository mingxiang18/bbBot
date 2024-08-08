package com.bb.bot.handler.horseRace;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.bb.bot.common.util.ResourcesUtils;
import com.bb.bot.handler.horseRace.entity.Horse;
import com.bb.bot.handler.horseRace.entity.HorseRaceEvent;
import com.bb.bot.util.SpringUtils;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 赛马小游戏
 * @author ren
 */
public class HorseRaceGame {
    /**
     * 马的数量
     */
    private static final int HORSE_NUM = 7;
    /**
     * 马移动距离
     */
    private static final int HORSE_MOVE_LENGTH = 2;
    /**
     * 赛道长度
     */
    private static final int SLIDE_LENGTH = 18;
    /**
     * 全局事件概率(单位%)
     */
    private static final int GLOBAL_EVENT_RATE = 50;
    /**
     * 独立事件概率(单位%)
     */
    private static final int INDEPENDENT_EVENT_RATE = 80;
    /**
     * 马图标
     */
    private static final String HORSE_CHAR = "🐎";
    /**
     * 马的属性和位置
     */
    private final List<Horse> horseList = new CopyOnWriteArrayList<>();

    /**
     * 比赛状态，0-未开始，1-进行中，2-结束
     */
    private AtomicInteger raceState = new AtomicInteger(0);

    /**
     * 赛道图标
     */
    private static final char SLIDE = 'Ξ';
    /**
     * 马的名称
     */
    private static final String[] HORSE_NAMES = {
            "闪电", "风驰", "骁勇", "赤焰", "银箭", "黑影", "星辰", "迅捷", "雷霆", "白雪",
            "奔雷", "赤兔", "飞羽", "火焰", "金星", "夜影", "流星", "烈风", "苍穹", "霜雪"
    };

    /**
     * 马的个性
     */
    private static final String[] HORSE_TRAITS = {
            "速度极快", "耐力出众", "灵活敏捷", "体型健硕", "善于冲刺", "持久力强", "反应迅速", "勇猛无畏",
            "稳重可靠", "意志坚定", "聪明机智", "力量惊人", "跳跃能力强", "步伐轻盈", "爆发力强", "适应力强",
            "胆识过人", "心态平稳", "精力充沛", "热情洋溢"
    };

    /**
     * 马的描述
     */
    private static final String[] HORSE_PERFORMANCE = {
            "在比赛中总是领先", "在关键时刻表现优异", "经常赢得比赛", "以稳健著称", "爆发力十足", "常常超越对手",
            "具备卓越的竞赛能力", "表现极为稳定", "以冷静闻名", "能在任何条件下表现出色", "在赛场上非常耀眼",
            "以绝对优势取胜", "总是充满斗志", "以迅猛的速度著称", "以绝妙的技巧著称", "能在长时间比赛中保持优势",
            "在起跑时表现出色", "以高超的耐力而闻名", "在激烈的竞争中脱颖而出", "经常创造新的纪录"
    };

    public HorseRaceGame() {
        initHorses();
    }

    private void initHorses() {
        Random random = new Random();
        for (int i = 0; i < HORSE_NUM; i++) {
            //初始化马添加到列表
            horseList.add(new Horse(i + 1,
                    HORSE_NAMES[random.nextInt(HORSE_NAMES.length)],
                    HORSE_CHAR,
                    String.format("%s是一匹%s的马，%s。", (i+1) + "号位", HORSE_TRAITS[random.nextInt(HORSE_TRAITS.length)], HORSE_PERFORMANCE[random.nextInt(HORSE_PERFORMANCE.length)]),
                    HORSE_MOVE_LENGTH,
                    0));
        }
    }

    /**
     * 获取马描述
     */
    public String generateHorseDescriptions() {
        StringBuilder sb = new StringBuilder();
        for (Horse horse : horseList) {
            sb.append(horse.getDescription()).append("\n");
        }

        return sb.toString();
    }

    /**
     * 添加赛马
     */
    public void addHorse(String horseIcon) {
        if (raceState.get() != 0) {
            throw new IllegalArgumentException("比赛进行中或已结束，无法添加马匹");
        }
        //初始化马添加到列表
        Random random = new Random();
        horseList.add(new Horse(horseList.size() + 1,
                HORSE_NAMES[random.nextInt(HORSE_NAMES.length)],
                StringUtils.isBlank(horseIcon) ? HORSE_CHAR : horseIcon,
                String.format("%s是一匹%s的马，%s。", (horseList.size() + 1) + "号位", HORSE_TRAITS[random.nextInt(HORSE_TRAITS.length)], HORSE_PERFORMANCE[random.nextInt(HORSE_PERFORMANCE.length)]),
                HORSE_MOVE_LENGTH,
                0));
    }

    /**
     * 获取马的赛道位置
     */
    public String printHorses() {
        StringBuilder horsePosition = new StringBuilder();
        for (Horse horse : horseList) {
            int position = horse.getNowPosition();
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < SLIDE_LENGTH; j++) {
                if (j == position) {
                    sb.append(horse.getIcon());
                } else {
                    sb.append(SLIDE);
                }
            }
            horsePosition.append(horse.getSeq()).append(" ").append(sb.toString()).append("\n");
        }
        horsePosition.append("\n");
        return horsePosition.toString();
    }

    /**
     * 马的移动事件
     */
    @SneakyThrows
    public String moveHorses() {
        raceState = new AtomicInteger(1);

        Random rand = new Random();
        StringBuilder eventContent = new StringBuilder();

        String horseRaceEventJson = new String(SpringUtils.getBean(ResourcesUtils.class).getStaticResourceToByte("horseRace/horseRaceEvent.json"), StandardCharsets.UTF_8);
        List<HorseRaceEvent> horseRaceEventList = JSON.parseObject(horseRaceEventJson, new TypeReference<List<HorseRaceEvent>>() {});

        //马的独立事件
        List<HorseRaceEvent> independentEventList = horseRaceEventList.stream().filter(event -> event.getEventType() == 1).toList();
        //赛马的全局事件
        List<HorseRaceEvent> globalEventList = horseRaceEventList.stream().filter(event -> event.getEventType() == 2).toList();

        //随机全局事件
        int globalRandomEvent = rand.nextInt(100);
        HorseRaceEvent globalEvent = null;
        if (globalRandomEvent > GLOBAL_EVENT_RATE) {
            globalEvent = globalEventList.get(rand.nextInt(globalEventList.size()));
            //添加到描述文本
            eventContent.append(globalEvent.getEventName()).append("\n");
        }

        for (int i = 0; i < horseList.size(); i++) {
            Horse horse = horseList.get(i);
            int slideChange = horse.getMoveLength();
            if (globalEvent != null) {
                //全局事件时，所有马都受到影响
                slideChange = slideChange + globalEvent.getEventEffect();
            }

            //马匹再单独判断随机事件
            int independentRandomEvent = rand.nextInt(100);
            if (independentRandomEvent > INDEPENDENT_EVENT_RATE) {
                HorseRaceEvent independentEvent = independentEventList.get(rand.nextInt(independentEventList.size()));
                slideChange = slideChange + independentEvent.getEventEffect();
                //添加到描述文本
                eventContent.append(independentEvent.getEventName().replace("{random_horse}", (i + 1) + "号马")).append("\n");
            }

            horse.setNowPosition(horse.getNowPosition() + slideChange);
            if (horse.getNowPosition() < 0) {
                horse.setNowPosition(0);
            } else if (horse.getNowPosition() >= SLIDE_LENGTH) {
                horse.setNowPosition(SLIDE_LENGTH - 1);
            }
        }

        return eventContent.toString();
    }

    /**
     * 结束比赛
     */
    public void finishGame() {
        raceState = new AtomicInteger(2);
    }

    /**
     * 判断比赛结束事件
     */
    public boolean raceFinished() {
        if (raceState.get() == 2) {
            return true;
        }

        for (Horse horse : horseList) {
            if (horse.getNowPosition() >= SLIDE_LENGTH - 1) {
                raceState = new AtomicInteger(2);
                return true;
            }
        }
        return false;
    }

    /**
     * 获取马匹胜利信息
     */
    public String getWinHorseContent() {
        //判断胜利马匹
        List<Integer> winNumList = new ArrayList<>();
        for (int i = 0; i < horseList.size(); i++) {
            int position = horseList.get(i).getNowPosition();
            if (position >= SLIDE_LENGTH - 1) {
                winNumList.add(i);
            }
        }
        return "比赛结束！" + winNumList.stream().map(winNum -> (winNum + 1) + "号马").collect(Collectors.joining("、")) + "获胜！";
    }

    /**
     * 控制台测试用
     */
    public void startRace() {
        System.out.println("赛马比赛开始！");
        //输出马的起始位置
        String horsePosition = printHorses();
        System.out.println(horsePosition);
        while (!raceFinished()) {
            //随机事件，并按照随机事件移动马匹
            String horseEvent = moveHorses();
            System.out.print(horseEvent);
            //输出马的当前位置
            horsePosition = printHorses();
            System.out.println(horsePosition);
            try {
                Thread.sleep(2000); // Adjust sleep time as per your preference
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        //判断胜利马匹
        System.out.println(getWinHorseContent());
    }

    public static void main(String[] args) {
        HorseRaceGame game = new HorseRaceGame();
        System.out.println(game.generateHorseDescriptions());
        game.startRace();
    }
}

