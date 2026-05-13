package com.bb.bot.handler.splatoon;

import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.api.BbMessageApi;
import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.common.util.RestUtils;
import com.bb.bot.constant.BotType;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.bb.BbSendMessage;
import com.bb.bot.handler.splatoon.render.ScheduleMapRenderer;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.io.File;
import java.util.Arrays;

/**
 * 喷喷事件处理器。所有图像绘制下沉到 {@link ScheduleMapRenderer}。
 * 本类只负责命令解析 + HTTP 拉数据 + 发消息。
 *
 * @author ren
 */
@Slf4j
@BootEventHandler(botType = BotType.BB, name = "斯普拉遁3")
public class BbSplatoonHandler {

    @Autowired
    private BbMessageApi bbMessageApi;

    @Autowired
    private RestUtils restUtils;

    @Autowired
    private ScheduleMapRenderer scheduleMapRenderer;

    /** splatoon3对战地图获取 */
    @Rule(eventType = EventType.MESSAGE, ruleType = RuleType.REGEX, keyword = {"^/?(下*图|全图)$"}, name = "对战地图获取")
    public void regularMapHandle(BbReceiveMessage bbReceiveMessage) {
        int timeIndex = parseTimeIndex(bbReceiveMessage.getMessage(), '下');

        JSONObject dataObject = fetchSchedules();

        File imageFile = scheduleMapRenderer.writeRegularMap(dataObject, timeIndex);

        sendImage(bbReceiveMessage, imageFile);
    }

    /** splatoon3打工地图获取 */
    @Rule(eventType = EventType.MESSAGE, ruleType = RuleType.REGEX, keyword = {"^/?(下*工|全工)$"}, name = "打工地图获取")
    public void coopMapHandle(BbReceiveMessage bbReceiveMessage) {
        int timeIndex = parseTimeIndex(bbReceiveMessage.getMessage(), '下');

        JSONObject dataObject = fetchSchedules();

        File imageFile = scheduleMapRenderer.writeCoopMap(dataObject, timeIndex);

        sendImage(bbReceiveMessage, imageFile);
    }

    /** splatoon3祭典日程 */
    @SneakyThrows
    @Rule(eventType = EventType.MESSAGE, ruleType = RuleType.MATCH, keyword = {"/祭典", "/上祭典", "/上上祭典", "祭典", "上祭典", "上上祭典"}, name = "祭典日程")
    public void festivalHandle(BbReceiveMessage bbReceiveMessage) {
        int timeIndex = parseTimeIndex(bbReceiveMessage.getMessage(), '上');

        HttpHeaders httpHeaders = jsonHeaders();
        JSONObject dataObject = restUtils.get("https://splatoon3.ink/data/festivals.json", httpHeaders, JSONObject.class).getJSONObject("JP").getJSONObject("data");
        JSONObject transferObject = restUtils.get("https://splatoon3.ink/data/locale/zh-CN.json", httpHeaders, JSONObject.class);

        JSONObject festivalObject = dataObject.getJSONObject("festRecords").getJSONArray("nodes").getJSONObject(timeIndex);

        File imageFile = scheduleMapRenderer.writeFestivalPoster(festivalObject, transferObject);

        sendImage(bbReceiveMessage, imageFile);
    }

    /** splatoon3活动日程 */
    @SneakyThrows
    @Rule(eventType = EventType.MESSAGE, ruleType = RuleType.MATCH, keyword = {"/活动", "活动"}, name = "活动日程")
    public void eventHandle(BbReceiveMessage bbReceiveMessage) {
        HttpHeaders httpHeaders = jsonHeaders();
        JSONObject dataObject = restUtils.get("https://splatoon3.ink/data/schedules.json", httpHeaders, JSONObject.class).getJSONObject("data");
        JSONObject transferObject = restUtils.get("https://splatoon3.ink/data/locale/zh-CN.json", httpHeaders, JSONObject.class);

        File imageFile = scheduleMapRenderer.writeEventPoster(dataObject, transferObject);

        sendImage(bbReceiveMessage, imageFile);
    }

    private JSONObject fetchSchedules() {
        return restUtils.get("https://splatoon3.ink/data/schedules.json", jsonHeaders(), JSONObject.class).getJSONObject("data");
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.5; Windows NT)");
        return headers;
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
