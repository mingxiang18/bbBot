package com.bb.bot.handler.russianRoulette;

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
import com.bb.bot.handler.russianRoulette.engine.RouletteNarrativeProvider;
import com.bb.bot.handler.russianRoulette.engine.RouletteState;
import com.bb.bot.handler.russianRoulette.engine.RussianRouletteEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.Optional;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 俄罗斯转盘事件处理器。状态走 {@link GameStateStore}，重启不丢；
 * 规则在 {@link RussianRouletteEngine}；叙事在 {@link RouletteNarrativeProvider}；
 * handler 自身只负责协议解析与回复。
 *
 * @author ren
 */
@Slf4j
@BootEventHandler(botType = BotType.BB, name = "俄罗斯转盘")
public class RussianRouletteHandler {

    private static final String GAME_TYPE = "ROULETTE";
    private static final Pattern CHAMBER_SIZE_PATTERN = Pattern.compile("^/?俄罗斯转盘(\\d+)");

    @Autowired
    private BbMessageApi bbMessageApi;

    @Autowired
    private GameStateStore gameStateStore;

    @Autowired
    private RouletteNarrativeProvider narrativeProvider;

    private final RussianRouletteEngine engine = new RussianRouletteEngine();
    private final Random random = new Random();

    @Rule(eventType = EventType.MESSAGE, messageType = MessageType.GROUP, needAtMe = true,
            ruleType = RuleType.REGEX, keyword = {"^/?俄罗斯转盘(\\s)?"}, name = "俄罗斯转盘")
    public void initGameHandle(BbReceiveMessage msg) {
        int chamberSize = 6;
        Matcher matcher = CHAMBER_SIZE_PATTERN.matcher(msg.getMessage());
        if (matcher.find()) {
            chamberSize = Integer.parseInt(matcher.group(1));
        }
        try {
            RouletteState state = engine.newGame(chamberSize);
            saveState(msg.getGroupId(), state);
            sendText(msg, "俄罗斯转盘游戏开始，已装填弹匣容量：" + chamberSize + "，已装填子弹：1，请各位玩家准备好。" +
                    "发送\"参与俄罗斯转盘\"可进行游戏参与，发送\"旋转弹匣\"可旋转左轮手枪内部弹匣, " +
                    "发送\"开枪\"将会对自己发射子弹, 发送\"中止俄罗斯转盘\"可结束比赛");
        } catch (IllegalArgumentException e) {
            sendText(msg, e.getMessage());
        }
    }

    @Rule(eventType = EventType.MESSAGE, messageType = MessageType.GROUP, needAtMe = true,
            ruleType = RuleType.MATCH, keyword = {"参与俄罗斯转盘", "/参与俄罗斯转盘"}, name = "参与俄罗斯转盘")
    public void joinGameHandle(BbReceiveMessage msg) {
        runOnState(msg, state -> {
            engine.join(state, msg.getUserId());
            saveState(msg.getGroupId(), state);
            sendText(msg, "玩家" + msg.getUserId() + "已参与游戏");
        });
    }

    @Rule(eventType = EventType.MESSAGE, messageType = MessageType.GROUP, needAtMe = true,
            ruleType = RuleType.MATCH, keyword = {"旋转弹匣", "/旋转弹匣"}, name = "旋转弹匣")
    public void spinChamberHandle(BbReceiveMessage msg) {
        runOnState(msg, state -> {
            engine.spin(state, msg.getUserId());
            saveState(msg.getGroupId(), state);
            sendText(msg, "弹匣已旋转，命运的子弹转向了未知的位置");
        });
    }

    @Rule(eventType = EventType.MESSAGE, messageType = MessageType.GROUP, needAtMe = true,
            ruleType = RuleType.MATCH, keyword = {"开枪", "/开枪"}, name = "对自己开枪")
    public void pullTriggerHandle(BbReceiveMessage msg) {
        runOnState(msg, state -> {
            boolean dead = engine.pullTrigger(state, msg.getUserId());
            if (dead) {
                gameStateStore.clear(GAME_TYPE, msg.getGroupId());
                sendText(msg, narrativeProvider.randomDeath(random) + "\n游戏结束");
            } else {
                saveState(msg.getGroupId(), state);
                sendText(msg, narrativeProvider.randomSurvive(random));
            }
        });
    }

    @Rule(eventType = EventType.MESSAGE, messageType = MessageType.GROUP, needAtMe = true,
            ruleType = RuleType.MATCH, keyword = {"中止俄罗斯转盘", "/中止俄罗斯转盘"}, name = "中止俄罗斯转盘")
    public void endGameHandle(BbReceiveMessage msg) {
        gameStateStore.clear(GAME_TYPE, msg.getGroupId());
        sendText(msg, "俄罗斯转盘游戏已中止");
    }

    private void runOnState(BbReceiveMessage msg, java.util.function.Consumer<RouletteState> body) {
        Optional<RouletteState> stateOpt = loadState(msg.getGroupId());
        if (stateOpt.isEmpty()) {
            sendText(msg, "当前没有进行中的俄罗斯转盘游戏，请发送\"俄罗斯转盘\"开始游戏");
            return;
        }
        try {
            body.accept(stateOpt.get());
        } catch (IllegalArgumentException e) {
            sendText(msg, e.getMessage());
        }
    }

    private Optional<RouletteState> loadState(String groupId) {
        Optional<UserConfigValue> row = gameStateStore.findActive(GAME_TYPE, groupId);
        if (row.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(JSON.parseObject(row.get().getValueName(), RouletteState.class));
        } catch (Exception e) {
            log.warn("Failed to deserialize roulette state for group {}, dropping", groupId, e);
            gameStateStore.clear(GAME_TYPE, groupId);
            return Optional.empty();
        }
    }

    private void saveState(String groupId, RouletteState state) {
        gameStateStore.save(GAME_TYPE, groupId, JSON.toJSONString(state));
    }

    private void sendText(BbReceiveMessage source, String text) {
        BbSendMessage out = new BbSendMessage(source);
        out.setMessageList(Collections.singletonList(BbMessageContent.buildTextContent(text)));
        bbMessageApi.sendMessage(out);
    }
}
