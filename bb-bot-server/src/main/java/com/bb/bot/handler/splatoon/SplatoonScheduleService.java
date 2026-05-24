package com.bb.bot.handler.splatoon;

import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.common.util.RestUtils;
import com.bb.bot.handler.splatoon.render.ScheduleMapRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.File;

/**
 * Splatoon3 日程的「拉数据 + 渲染成图」共用逻辑。
 *
 * <p>从 {@link BbSplatoonHandler} 抽出，供它（关键词命令触发）和 AI 工具
 * （{@code SplatoonScheduleTool}，自然语言识别意图触发）共同复用，避免两处重复
 * 抓取 splatoon3.ink 的 schedules/festivals JSON。</p>
 *
 * <p>数据源：splatoon3.ink（非官方但跟随官方 API）。走项目统一的 RestUtils（RestClientConfig
 * 配了代理），否则国内抓不到。</p>
 */
@Service
public class SplatoonScheduleService {

    private static final String SCHEDULES_URL = "https://splatoon3.ink/data/schedules.json";
    private static final String FESTIVALS_URL = "https://splatoon3.ink/data/festivals.json";
    private static final String LOCALE_URL = "https://splatoon3.ink/data/locale/zh-CN.json";

    @Autowired
    private RestUtils restUtils;

    @Autowired
    private ScheduleMapRenderer scheduleMapRenderer;

    /** 对战地图。timeIndex：0=当前，越大越靠后的时段，-1=全部时段一张图。 */
    public File renderRegularMap(int timeIndex) {
        return scheduleMapRenderer.writeRegularMap(fetchSchedules(), timeIndex);
    }

    /** 打工（鲑鱼跑）地图 + 支给武器。timeIndex 同上。 */
    public File renderCoopMap(int timeIndex) {
        return scheduleMapRenderer.writeCoopMap(fetchSchedules(), timeIndex);
    }

    /** 祭典海报。timeIndex：0=当前/最近，越大越早的往届。 */
    public File renderFestivalPoster(int timeIndex) {
        HttpHeaders headers = jsonHeaders();
        JSONObject data = restUtils.get(FESTIVALS_URL, headers, JSONObject.class)
                .getJSONObject("JP").getJSONObject("data");
        JSONObject transferObject = restUtils.get(LOCALE_URL, headers, JSONObject.class);
        JSONObject festivalObject = data.getJSONObject("festRecords").getJSONArray("nodes").getJSONObject(timeIndex);
        return scheduleMapRenderer.writeFestivalPoster(festivalObject, transferObject);
    }

    /** 活动（打工以外的限时赛事）海报。 */
    public File renderEventPoster() {
        HttpHeaders headers = jsonHeaders();
        JSONObject data = restUtils.get(SCHEDULES_URL, headers, JSONObject.class).getJSONObject("data");
        JSONObject transferObject = restUtils.get(LOCALE_URL, headers, JSONObject.class);
        return scheduleMapRenderer.writeEventPoster(data, transferObject);
    }

    private JSONObject fetchSchedules() {
        return restUtils.get(SCHEDULES_URL, jsonHeaders(), JSONObject.class).getJSONObject("data");
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.5; Windows NT)");
        return headers;
    }
}
