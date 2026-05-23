package com.bb.bot.aiAgent.tools;

import com.bb.bot.aiAgent.core.AiTool;
import com.bb.bot.aiAgent.core.AiToolParam;
import com.bb.bot.handler.splatoon.SplatoonScheduleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Splatoon3（斯普拉遁3）日程查询工具，自然语言入口。
 *
 * <p>取代旧的 {@code Splatoon3CoopTool}（那个只返回打工 JSON，且和 {@link SplatoonScheduleService}
 * 重复抓取）。本工具复用 {@code BbSplatoonHandler} 同一套渲染逻辑，直接把日程图发回当前会话。</p>
 *
 * <p>定位：{@code BbSplatoonHandler} 的关键词命令（下工 / 全图 / 祭典…）严格命中时优先直接处理；
 * 用户没严格命中、但自然语言表达了查日程意图时，模型识别后调本工具走同一逻辑——
 * 即「模糊匹配兜底，优先级低于严格匹配」。</p>
 */
@Slf4j
@Component
public class SplatoonScheduleTool {

    @Autowired
    private SplatoonScheduleService splatoonScheduleService;

    @AiTool(
            name = "splatoon3_schedule",
            description = "查询 Splatoon3（斯普拉遁3 / 喷喷）的日程并以图片形式发回当前会话。"
                    + "用户问「现在/下一场什么图、打工(鲑鱼跑/salmon run)什么图什么武器、祭典、活动」等时调用。"
                    + "mode：regular=对战地图，coop=打工地图，festival=祭典，event=活动。"
                    + "timeIndex：0=当前；对战/打工 越大越靠后的时段，-1=全部时段一张图；"
                    + "祭典 越大越早的往届；event 忽略此参数。"
                    + "图片会直接发到会话，你拿到 ok 后只需简短附一句话，不要复述图片内容。"
    )
    public Map<String, Object> schedule(
            @AiToolParam(name = "mode", description = "regular / coop / festival / event")
            String mode,
            @AiToolParam(name = "timeIndex", description = "时段序号，默认 0=当前；-1=全部（仅对战/打工）", required = false)
            Integer timeIndex) {
        Map<String, Object> result = new LinkedHashMap<>();

        AgentReplySink sink = AgentReplyContext.get();
        if (sink == null) {
            result.put("error", "no_active_conversation");
            result.put("hint", "当前不在可回传的会话里（如定时任务），无法发送图片");
            return result;
        }
        if (!sink.imageSupported()) {
            result.put("error", "client_no_image_capability");
            result.put("hint", "对方客户端不支持接收图片，请改用文字告知");
            return result;
        }

        String m = mode == null ? "" : mode.trim().toLowerCase();
        int idx = timeIndex == null ? 0 : timeIndex;
        try {
            File image;
            switch (m) {
                case "regular":
                    image = splatoonScheduleService.renderRegularMap(idx);
                    break;
                case "coop":
                    image = splatoonScheduleService.renderCoopMap(idx);
                    break;
                case "festival":
                    image = splatoonScheduleService.renderFestivalPoster(Math.max(idx, 0));
                    break;
                case "event":
                    image = splatoonScheduleService.renderEventPoster();
                    break;
                default:
                    result.put("error", "invalid_mode");
                    result.put("hint", "mode 取 regular / coop / festival / event");
                    return result;
            }
            sink.sendImage(image);
            result.put("ok", true);
            result.put("mode", m);
            result.put("timeIndex", idx);
            result.put("note", "日程图已发送到当前会话");
            return result;
        } catch (Exception e) {
            log.warn("splatoon3_schedule 失败 mode={} idx={}", m, idx, e);
            result.put("error", "render_failed");
            result.put("message", e.getMessage());
            return result;
        }
    }
}
