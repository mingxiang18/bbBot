package com.bb.bot.handler.horseRace;

import com.bb.bot.api.BbMessageApi;
import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.MessageType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.constant.BotType;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.bb.BbSendMessage;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 赛马小游戏事件处理器
 * @author ren
 */
@BootEventHandler(botType = BotType.BB, name = "赛马小游戏")
public class HorseRaceHandler {

    @Autowired
    private BbMessageApi bbMessageApi;

    /**
     * emoji表情正则
     */
    private final Pattern emojiPattern = Pattern.compile("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+");

    /**
     * 进行中的赛马游戏map
     */
    private static Map<String, HorseRaceGame> horseRaceGameMap = new ConcurrentHashMap<>();

    @Rule(eventType = EventType.MESSAGE, messageType = MessageType.GROUP, needAtMe = true,
            ruleType = RuleType.MATCH, keyword = {"赛马", "/赛马"}, name = "赛马")
    public void initGameHandle(BbReceiveMessage bbReceiveMessage) {
        //初始化游戏
        HorseRaceGame horseRaceGame = initGame(bbReceiveMessage.getGroupId());

        BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
        bbSendMessage.setMessageList(Collections.singletonList(BbMessageContent.buildTextContent("赛马开始，当前有以下马匹，请选择你要下注的马匹，" +
                "发送“下注1号位”可进行下注，发送“添加马匹[emoji表情]”可添加马匹, 发送“开始赛马”可进行比赛, 发送“中止赛马”可结束比赛\n\n" +
                horseRaceGame.generateHorseDescriptions())));
        bbMessageApi.sendMessage(bbSendMessage);
    }

    @Rule(eventType = EventType.MESSAGE, messageType = MessageType.GROUP, needAtMe = true,
            ruleType = RuleType.REGEX, keyword = {"^/?下注(\\d+)号位"}, name = "下注赛马")
    public void betGameHandle(BbReceiveMessage bbReceiveMessage) {
        try {
            //获取游戏
            HorseRaceGame horseRaceGame = getGame(bbReceiveMessage.getGroupId());

            BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
            bbSendMessage.setMessageList(Collections.singletonList(BbMessageContent.buildTextContent("玩家" + bbReceiveMessage.getUserId() + "下注成功")));
            bbMessageApi.sendMessage(bbSendMessage);
        }catch (Exception e) {
            BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
            bbSendMessage.setMessageList(Collections.singletonList(BbMessageContent.buildTextContent(e.getMessage())));
            bbMessageApi.sendMessage(bbSendMessage);
        }
    }

    @Rule(eventType = EventType.MESSAGE, messageType = MessageType.GROUP, needAtMe = true,
            ruleType = RuleType.REGEX, keyword = {"^/?添加马匹\\s?"}, name = "添加马匹")
    public void addHorseHandle(BbReceiveMessage bbReceiveMessage) {
        try {
            Matcher matcher = emojiPattern.matcher(bbReceiveMessage.getMessage());
            if (matcher.find()) {
                String emoji = matcher.group();
                //获取游戏
                HorseRaceGame horseRaceGame = getGame(bbReceiveMessage.getGroupId());
                //添加马匹
                horseRaceGame.addHorse(emoji);

                BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
                bbSendMessage.setMessageList(Collections.singletonList(BbMessageContent.buildTextContent("添加马匹成功，当前马匹如下\n" + horseRaceGame.printHorses())));
                bbMessageApi.sendMessage(bbSendMessage);
            }else {
                throw new IllegalArgumentException("马匹图标格式不正确，只能为emoji表情");
            }
        }catch (Exception e) {
            BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
            bbSendMessage.setMessageList(Collections.singletonList(BbMessageContent.buildTextContent(e.getMessage())));
            bbMessageApi.sendMessage(bbSendMessage);
        }
    }

    @Rule(eventType = EventType.MESSAGE, messageType = MessageType.GROUP, needAtMe = true,
            ruleType = RuleType.MATCH, keyword = {"开始赛马", "/开始赛马"}, name = "开始赛马")
    public void startGameHandle(BbReceiveMessage bbReceiveMessage) {
        try {
            //获取游戏
            HorseRaceGame horseRaceGame = getGame(bbReceiveMessage.getGroupId());

            //发送开始消息
            BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
            bbSendMessage.setMessageList(Collections.singletonList(BbMessageContent.buildTextContent("赛马比赛开始！\n" + horseRaceGame.printHorses())));
            bbMessageApi.sendMessage(bbSendMessage);
            //休眠2秒
            Thread.sleep(2000);

            //循环遍历移动马匹，输出位置
            while (!horseRaceGame.raceFinished()) {
                //随机事件，并按照随机事件移动马匹
                String horseEvent = horseRaceGame.moveHorses();
                //输出马的当前位置
                String horsePosition = horseRaceGame.printHorses();

                //发送比赛实况消息
                bbSendMessage.setMessageList(Collections.singletonList(BbMessageContent.buildTextContent(horseEvent + horsePosition)));
                bbMessageApi.sendMessage(bbSendMessage);
                Thread.sleep(4000); //休眠2秒
            }

            //发送胜利消息
            bbSendMessage.setMessageList(Collections.singletonList(BbMessageContent.buildTextContent(horseRaceGame.getWinHorseContent())));
            bbMessageApi.sendMessage(bbSendMessage);
        }catch (Exception e) {
            BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
            bbSendMessage.setMessageList(Collections.singletonList(BbMessageContent.buildTextContent(e.getMessage())));
            bbMessageApi.sendMessage(bbSendMessage);
        }finally {
            //移除游戏
            deleteGame(bbReceiveMessage.getGroupId());
        }
    }

    @Rule(eventType = EventType.MESSAGE, messageType = MessageType.GROUP, needAtMe = true,
            ruleType = RuleType.MATCH, keyword = {"中止赛马", "/中止赛马"}, name = "中止赛马")
    public void endGameHandle(BbReceiveMessage bbReceiveMessage) {
        try {
            //获取游戏
            HorseRaceGame horseRaceGame = getGame(bbReceiveMessage.getGroupId());
            horseRaceGame.finishGame();
        }catch (Exception e) {
            BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
            bbSendMessage.setMessageList(Collections.singletonList(BbMessageContent.buildTextContent(e.getMessage())));
            bbMessageApi.sendMessage(bbSendMessage);
        }finally {
            //移除游戏
            deleteGame(bbReceiveMessage.getGroupId());
        }
    }

    /**
     * 初始化指定id的赛马比赛
     */
    private static HorseRaceGame initGame(String id) {
        HorseRaceGame game = new HorseRaceGame();
        horseRaceGameMap.put(id, game);
        return game;
    }

    /**
     * 获取指定id的赛马比赛
     */
    private static HorseRaceGame getGame(String id) {
        if (!horseRaceGameMap.containsKey(id)) {
            throw new IllegalArgumentException("赛马游戏不存在，请发送“赛马”开始游戏");
        }else {
            return horseRaceGameMap.get(id);
        }
    }

    /**
     * 删除指定id的赛马比赛
     */
    private static void deleteGame(String id) {
        horseRaceGameMap.remove(id);
    }
}
