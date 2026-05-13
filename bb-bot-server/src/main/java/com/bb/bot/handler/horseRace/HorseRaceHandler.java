package com.bb.bot.handler.horseRace;

import com.alibaba.fastjson2.JSON;
import com.bb.bot.api.BbMessageApi;
import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.MessageType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.constant.BotType;
import com.bb.bot.database.userConfigInfo.entity.UserConfigValue;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.bb.BbSendMessage;
import com.bb.bot.handler.game.GameStateStore;
import com.bb.bot.handler.horseRace.engine.HorseRaceEngine;
import com.bb.bot.handler.horseRace.engine.HorseRaceEventLoader;
import com.bb.bot.handler.horseRace.engine.HorseRaceState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 赛马 handler。负责协议解析；规则在 {@link HorseRaceEngine}，事件在
 * {@link HorseRaceEventLoader}，初始化阶段的状态走 {@link GameStateStore}。
 *
 * @author ren
 */
@Slf4j
@BootEventHandler(botType = BotType.BB, name = "赛马小游戏")
public class HorseRaceHandler {

    private static final String GAME_TYPE = "HORSE_RACE";
    private static final long TICK_DELAY_MS = 4000L;
    private static final long INITIAL_DELAY_MS = 2000L;
    private final Pattern emojiPattern = Pattern.compile("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+");

    @Autowired
    private BbMessageApi bbMessageApi;

    @Autowired
    private GameStateStore gameStateStore;

    @Autowired
    private HorseRaceEventLoader eventLoader;

    private final HorseRaceEngine engine = new HorseRaceEngine();

    @Rule(eventType = EventType.MESSAGE, messageType = MessageType.GROUP, needAtMe = true,
            ruleType = RuleType.MATCH, keyword = {"赛马", "/赛马"}, name = "赛马")
    public void initGameHandle(BbReceiveMessage msg) {
        HorseRaceState state = engine.newGame();
        saveState(msg.getGroupId(), state);
        sendText(msg, "赛马开始，当前有以下马匹，请选择你要下注的马匹，" +
                "发送\"下注1号位\"可进行下注，发送\"添加马匹[emoji表情]\"可添加马匹, " +
                "发送\"开始赛马\"可进行比赛, 发送\"中止赛马\"可结束比赛\n\n" +
                engine.describeHorses(state));
    }

    @Rule(eventType = EventType.MESSAGE, messageType = MessageType.GROUP, needAtMe = true,
            ruleType = RuleType.REGEX, keyword = {"^/?下注(\\d+)号位"}, name = "下注赛马")
    public void betGameHandle(BbReceiveMessage msg) {
        Optional<HorseRaceState> stateOpt = loadState(msg.getGroupId());
        if (stateOpt.isEmpty()) {
            sendText(msg, "赛马游戏不存在，请发送\"赛马\"开始游戏");
            return;
        }
        sendText(msg, "玩家" + msg.getUserId() + "下注成功");
    }

    @Rule(eventType = EventType.MESSAGE, messageType = MessageType.GROUP, needAtMe = true,
            ruleType = RuleType.REGEX, keyword = {"^/?添加马匹\\s?"}, name = "添加马匹")
    public void addHorseHandle(BbReceiveMessage msg) {
        Matcher matcher = emojiPattern.matcher(msg.getMessage());
        if (!matcher.find()) {
            sendText(msg, "马匹图标格式不正确，只能为emoji表情");
            return;
        }
        String emoji = matcher.group();

        Optional<HorseRaceState> stateOpt = loadState(msg.getGroupId());
        if (stateOpt.isEmpty()) {
            sendText(msg, "赛马游戏不存在，请发送\"赛马\"开始游戏");
            return;
        }
        try {
            HorseRaceState state = stateOpt.get();
            engine.addHorse(state, emoji);
            saveState(msg.getGroupId(), state);
            sendText(msg, "添加马匹成功，当前马匹如下\n" + engine.renderHorses(state));
        } catch (IllegalArgumentException e) {
            sendText(msg, e.getMessage());
        }
    }

    @Rule(eventType = EventType.MESSAGE, messageType = MessageType.GROUP, needAtMe = true,
            ruleType = RuleType.MATCH, keyword = {"开始赛马", "/开始赛马"}, name = "开始赛马")
    public void startGameHandle(BbReceiveMessage msg) {
        Optional<HorseRaceState> stateOpt = loadState(msg.getGroupId());
        if (stateOpt.isEmpty()) {
            sendText(msg, "赛马游戏不存在，请发送\"赛马\"开始游戏");
            return;
        }
        HorseRaceState state = stateOpt.get();
        int seq = 1;
        BbSendMessage out = new BbSendMessage(msg);
        try {
            out.setMessageList(Collections.singletonList(BbMessageContent.buildTextContent(
                    "赛马比赛开始！\n" + engine.renderHorses(state))));
            out.setMessageSeq(seq++);
            bbMessageApi.sendMessage(out);
            Thread.sleep(INITIAL_DELAY_MS);

            while (!engine.isFinished(state)) {
                String event = engine.tick(state, eventLoader.getEvents());
                String position = engine.renderHorses(state);
                out.setMessageList(Collections.singletonList(BbMessageContent.buildTextContent(event + position)));
                out.setMessageSeq(seq++);
                bbMessageApi.sendMessage(out);
                Thread.sleep(TICK_DELAY_MS);
            }

            out.setMessageList(Collections.singletonList(BbMessageContent.buildTextContent(engine.winnerSummary(state))));
            out.setMessageSeq(seq);
            bbMessageApi.sendMessage(out);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Horse race interrupted for group {}", msg.getGroupId());
        } finally {
            gameStateStore.clear(GAME_TYPE, msg.getGroupId());
        }
    }

    @Rule(eventType = EventType.MESSAGE, messageType = MessageType.GROUP, needAtMe = true,
            ruleType = RuleType.MATCH, keyword = {"中止赛马", "/中止赛马"}, name = "中止赛马")
    public void endGameHandle(BbReceiveMessage msg) {
        gameStateStore.clear(GAME_TYPE, msg.getGroupId());
        sendText(msg, "赛马已中止");
    }

    private Optional<HorseRaceState> loadState(String groupId) {
        Optional<UserConfigValue> row = gameStateStore.findActive(GAME_TYPE, groupId);
        if (row.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(JSON.parseObject(row.get().getValueName(), HorseRaceState.class));
        } catch (Exception e) {
            log.warn("Failed to deserialize horse race state for group {}, dropping", groupId, e);
            gameStateStore.clear(GAME_TYPE, groupId);
            return Optional.empty();
        }
    }

    private void saveState(String groupId, HorseRaceState state) {
        gameStateStore.save(GAME_TYPE, groupId, JSON.toJSONString(state));
    }

    private void sendText(BbReceiveMessage source, String text) {
        BbSendMessage out = new BbSendMessage(source);
        out.setMessageList(Collections.singletonList(BbMessageContent.buildTextContent(text)));
        bbMessageApi.sendMessage(out);
    }
}
