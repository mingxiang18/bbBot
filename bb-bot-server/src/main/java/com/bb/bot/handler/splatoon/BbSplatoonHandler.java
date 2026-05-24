package com.bb.bot.handler.splatoon;

import com.bb.bot.api.BbMessageApi;
import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.constant.BotType;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.bb.BbSendMessage;
import com.bb.bot.handler.splatoon.render.ScheduleMapRenderer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.util.Arrays;

/**
 * 喷喷事件处理器。拉数据 + 渲染下沉到 {@link SplatoonScheduleService}（图像绘制在
 * {@link ScheduleMapRenderer}）。本类只负责关键词命令解析 + 发消息。
 *
 * <p>同样的能力还通过 {@code SplatoonScheduleTool} 暴露为 AI 工具：用户没严格命中
 * 「下工 / 全图」这类关键词、但自然语言表达了查日程的意图时，由模型识别后调工具走同一逻辑。
 * 关键词命中优先级更高（这里直接处理，不耗 LLM）；工具是兜底的模糊匹配路径。</p>
 *
 * @author ren
 */
@Slf4j
@BootEventHandler(botType = BotType.BB, name = "斯普拉遁3")
public class BbSplatoonHandler {

    @Autowired
    private BbMessageApi bbMessageApi;

    @Autowired
    private SplatoonScheduleService splatoonScheduleService;

    /** splatoon3对战地图获取 */
    @Rule(eventType = EventType.MESSAGE, ruleType = RuleType.REGEX, keyword = {"^/?(下*图|全图)$"}, name = "对战地图获取")
    public void regularMapHandle(BbReceiveMessage bbReceiveMessage) {
        int timeIndex = parseTimeIndex(bbReceiveMessage.getMessage(), '下');
        sendImage(bbReceiveMessage, splatoonScheduleService.renderRegularMap(timeIndex));
    }

    /** splatoon3打工地图获取 */
    @Rule(eventType = EventType.MESSAGE, ruleType = RuleType.REGEX, keyword = {"^/?(下*工|全工)$"}, name = "打工地图获取")
    public void coopMapHandle(BbReceiveMessage bbReceiveMessage) {
        int timeIndex = parseTimeIndex(bbReceiveMessage.getMessage(), '下');
        sendImage(bbReceiveMessage, splatoonScheduleService.renderCoopMap(timeIndex));
    }

    /** splatoon3祭典日程 */
    @Rule(eventType = EventType.MESSAGE, ruleType = RuleType.MATCH, keyword = {"/祭典", "/上祭典", "/上上祭典", "祭典", "上祭典", "上上祭典"}, name = "祭典日程")
    public void festivalHandle(BbReceiveMessage bbReceiveMessage) {
        int timeIndex = parseTimeIndex(bbReceiveMessage.getMessage(), '上');
        sendImage(bbReceiveMessage, splatoonScheduleService.renderFestivalPoster(timeIndex));
    }

    /** splatoon3活动日程 */
    @Rule(eventType = EventType.MESSAGE, ruleType = RuleType.MATCH, keyword = {"/活动", "活动"}, name = "活动日程")
    public void eventHandle(BbReceiveMessage bbReceiveMessage) {
        sendImage(bbReceiveMessage, splatoonScheduleService.renderEventPoster());
    }

    /** 数前缀字符（下/上）出现次数；命中"全"返回 -1。 */
    private static int parseTimeIndex(String message, char counter) {
        int timeIndex = 0;
        for (char c : message.toCharArray()) {
            if (c == counter) {
                timeIndex++;
            }
            if (c == '全') {
                return -1;
            }
        }
        return timeIndex;
    }

    private void sendImage(BbReceiveMessage source, File imageFile) {
        BbSendMessage out = new BbSendMessage(source);
        out.setMessageList(Arrays.asList(
                BbMessageContent.buildAtMessageContent(source.getUserId()),
                BbMessageContent.buildLocalImageMessageContent(imageFile)));
        bbMessageApi.sendMessage(out);
    }
}
