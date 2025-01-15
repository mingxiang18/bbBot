package com.bb.bot.handler.russianRoulette;

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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 俄罗斯转盘事件处理器
 * @author ren
 */
@BootEventHandler(botType = BotType.BB, name = "俄罗斯转盘")
public class RussianRouletteHandler {

    @Autowired
    private BbMessageApi bbMessageApi;

    /**
     * 进行中的俄罗斯转盘游戏map
     */
    private static Map<String, RussianRoulette> russianRouletteMap = new ConcurrentHashMap<>();

    private static final List<String> notDeathContentList = Arrays.asList(
            "枪声应该在这一刻响起，但只有沉默，你幸运地躲过了死神的手指。",
            "扳机被扣下，但没有枪声响起，你庆幸自己逃过了一劫。",
            "子弹没有射出，你的生命得以延续，命运仿佛在与你开玩笑。",
            "枪响之前的沉默，让你意识到子弹的缺失，你的生命在最后一刻得以挽救。",
            "枪声缺失，你的命运被重新书写，幸运之神似乎在庇佑着你。",
            "在那寂静的一刻，子弹没有发射，你仿佛听到了死神的叹息。",
            "命运之手轻抚你的肩，让你幸免于死，枪声没有响起。",
            "你的手指颤抖着扣下扳机，但只有寂静，你逃脱了死亡的魔爪。",
            "在千钧一发之际，枪声没有响起，你感受到了命运的援手。",
            "你感受到了死亡的阴影，但扳机下去，却只有空洞的沉默。",
            "子弹没有离开枪膛，你在命运的边缘舞动着，幸运之神在微笑。",
            "扳机下去，但枪声没有响起，你逃过了死亡的咆哮。",
            "在最后一刻，子弹未发射，你活了下来，命运似乎在调侃着你。",
            "寂静填满了房间，你扣下扳机，但只感受到了空虚，你逃过了一劫。",
            "你感受到了生死的边缘，但扳机下去，却只有寂静和虚无。",
            "在绝望中扣下扳机，但只有空洞的回响，你感受到了命运的轻抚。",
            "你猛地扣下扳机，但却只感觉到冰冷的金属，没有了死亡的颤抖，只有无尽的虚无。",
            "当你以为一切都将结束时，扳机被扣下，但子弹却未启程，留下你和死神之间的最后一线生机。",
            "扳机下去，但枪声却未响起，一切都停滞在那一刻，仿佛时间被永久冻结。",
            "你扣下扳机，但只听到了寂静的回响，没有了死亡的呼唤，你逃脱了一劫。",
            "你的手指紧紧扣住扳机，但只有冰冷的沉默，你幸运地躲过了死亡的阴影。");

    private static final List<String> deathContentList = Arrays.asList(
            "枪声震耳欲聋，子弹命中了你的胸膛，鲜血如泉涌般喷射而出，你倒在了血泊之中，生命在痛苦中终结。",
            "在那一声枪响中，子弹穿透了你的身体，带走了你的生命，你的存在在这一刻被彻底抹消。",
            "扣下扳机，伴随着枪声，子弹击中了你的脑袋，你的意识瞬间陷入黑暗，生命在瞬间消逝。",
            "一声尖锐的枪响，子弹狠狠地射出，你感受到了死神的触摸，生命在这一刻烟消云散。",
            "在最后的一声枪响中，子弹击中了你的头部，你的意识瞬间湮灭，被永远地封印在黑暗之中。",
            "扳机下去，伴随着枪声，子弹击中了你的头部，你跌倒在地，最终被死亡永远地吞噬。",
            "随着最后一声枪响，子弹击中了你的脑袋，你的脑袋瞬间爆裂，生命在这一刻彻底消逝。",
            "在最后的一声枪响中，子弹命中了你的要害，你瞬间失去了一切，生命在这一刻彻底消逝。",
            "\"嘭！\"，你听到了子弹的呼啸，你眼前一黑，失去了生命",
            "枪声还在耳边回响，子弹击中了你的脑袋，你的意识瞬间崩溃，生命在这一刻永远地消逝。",
            "在那一声枪响中，子弹命中了你的头部，你的脑袋瞬间爆裂，生命在这一刻彻底消逝。");

    @Rule(eventType = EventType.MESSAGE, messageType = MessageType.GROUP, needAtMe = true,
            ruleType = RuleType.REGEX, keyword = {"^/?俄罗斯转盘(\\s)?"}, name = "俄罗斯转盘")
    public void initGameHandle(BbReceiveMessage bbReceiveMessage) {
        // 定义正则表达式模式
        Pattern pattern = Pattern.compile("^/?俄罗斯转盘(\\d+)");
        Matcher matcher = pattern.matcher(bbReceiveMessage.getMessage());
        int chamberSize = 6;
        // 如果找到匹配项
        if (matcher.find()) {
            chamberSize = Integer.parseInt(matcher.group(1));
        }
        //初始化游戏
        RussianRoulette russianRoulette = initGame(bbReceiveMessage.getGroupId(), chamberSize);

        BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
        bbSendMessage.setMessageList(Collections.singletonList(BbMessageContent.buildTextContent("俄罗斯转盘游戏开始，" +
                "已装填弹匣容量：" + chamberSize + "，已装填子弹：1，请各位玩家准备好。" +
                "发送“参与俄罗斯转盘”可进行游戏参与，发送“旋转弹匣”可旋转左轮手枪内部弹匣, 发送“开枪”将会对自己发射子弹, 发送“中止俄罗斯转盘”可结束比赛")));
        bbMessageApi.sendMessage(bbSendMessage);
    }

    @Rule(eventType = EventType.MESSAGE, messageType = MessageType.GROUP, needAtMe = true,
            ruleType = RuleType.MATCH, keyword = {"参与俄罗斯转盘", "/参与俄罗斯转盘"}, name = "参与俄罗斯转盘")
    public void joinGameHandle(BbReceiveMessage bbReceiveMessage) {
        try {
            //获取游戏
            RussianRoulette russianRoulette = getGame(bbReceiveMessage.getGroupId());
            //玩家加入游戏
            russianRoulette.joinRussianRoulette(bbReceiveMessage.getUserId());

            BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
            bbSendMessage.setMessageList(Collections.singletonList(BbMessageContent.buildTextContent("玩家" + bbReceiveMessage.getUserId() + "已参与游戏")));
            bbMessageApi.sendMessage(bbSendMessage);
        }catch (Exception e) {
            BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
            bbSendMessage.setMessageList(Collections.singletonList(BbMessageContent.buildTextContent(e.getMessage())));
            bbMessageApi.sendMessage(bbSendMessage);
        }
    }

    @Rule(eventType = EventType.MESSAGE, messageType = MessageType.GROUP, needAtMe = true,
            ruleType = RuleType.MATCH, keyword = {"旋转弹匣", "/旋转弹匣"}, name = "旋转弹匣")
    public void spinChamberHandle(BbReceiveMessage bbReceiveMessage) {
        try {
            //获取游戏
            RussianRoulette russianRoulette = getGame(bbReceiveMessage.getGroupId());
            //旋转弹匣
            russianRoulette.spinChamber(bbReceiveMessage.getUserId());

            BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
            bbSendMessage.setMessageList(Collections.singletonList(BbMessageContent.buildTextContent("弹匣已旋转，命运的子弹转向了未知的位置")));
            bbMessageApi.sendMessage(bbSendMessage);
        }catch (Exception e) {
            BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
            bbSendMessage.setMessageList(Collections.singletonList(BbMessageContent.buildTextContent(e.getMessage())));
            bbMessageApi.sendMessage(bbSendMessage);
        }
    }

    @Rule(eventType = EventType.MESSAGE, messageType = MessageType.GROUP, needAtMe = true,
            ruleType = RuleType.MATCH, keyword = {"开枪", "/开枪"}, name = "对自己开枪")
    public void addHorseHandle(BbReceiveMessage bbReceiveMessage) {
        try {
            //获取游戏
            RussianRoulette russianRoulette = getGame(bbReceiveMessage.getGroupId());
            //扣下扳机
            boolean isBoom = russianRoulette.pullTrigger(bbReceiveMessage.getUserId());

            BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
            if (isBoom) {
                bbSendMessage.setMessageList(Collections.singletonList(BbMessageContent.buildTextContent(deathContentList.get(new Random().nextInt(deathContentList.size())) + "\n" + "游戏结束")));
                //结束游戏
                deleteGame(bbReceiveMessage.getGroupId());
            }else {
                bbSendMessage.setMessageList(Collections.singletonList(BbMessageContent.buildTextContent(notDeathContentList.get(new Random().nextInt(notDeathContentList.size())))));
            }
            bbMessageApi.sendMessage(bbSendMessage);
        }catch (Exception e) {
            BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
            bbSendMessage.setMessageList(Collections.singletonList(BbMessageContent.buildTextContent(e.getMessage())));
            bbMessageApi.sendMessage(bbSendMessage);
        }
    }

    @Rule(eventType = EventType.MESSAGE, messageType = MessageType.GROUP, needAtMe = true,
            ruleType = RuleType.MATCH, keyword = {"中止俄罗斯转盘", "/中止俄罗斯转盘"}, name = "中止俄罗斯转盘")
    public void endGameHandle(BbReceiveMessage bbReceiveMessage) {
        //结束游戏
        deleteGame(bbReceiveMessage.getGroupId());

        BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
        bbSendMessage.setMessageList(Collections.singletonList(BbMessageContent.buildTextContent("俄罗斯转盘游戏已中止")));
        bbMessageApi.sendMessage(bbSendMessage);
    }

    /**
     * 初始化指定id的比赛
     */
    private static RussianRoulette initGame(String id, Integer chamberSize) {
        RussianRoulette game = new RussianRoulette(chamberSize);
        russianRouletteMap.put(id, game);
        return game;
    }

    /**
     * 获取指定id的比赛
     */
    private static RussianRoulette getGame(String id) {
        if (!russianRouletteMap.containsKey(id)) {
            throw new IllegalArgumentException("当前没有进行中的俄罗斯转盘游戏，请发送“俄罗斯转盘”开始游戏");
        }else {
            return russianRouletteMap.get(id);
        }
    }

    /**
     * 删除指定id的比赛
     */
    private static void deleteGame(String id) {
        russianRouletteMap.remove(id);
    }
}
