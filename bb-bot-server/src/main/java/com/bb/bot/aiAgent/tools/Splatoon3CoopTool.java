package com.bb.bot.aiAgent.tools;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.aiAgent.core.AiTool;
import com.bb.bot.aiAgent.core.AiToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Splatoon3 打工 (Salmon Run) 专用工具。
 *
 * <p>背景：通用 web_search + http_fetch 让 AI 自己组装查询时，AI 会陷入「探索循环」——
 * 反复用不同语言 / 不同关键字搜，schedules.json 即使抓到了也容易在 chunk 截断处死亡。
 * 这是真实 token / 时延浪费的典型案例。给一个专用工具，AI 一步直达。</p>
 *
 * <p>数据源：splatoon3.ink/data/schedules.json （非官方但跟随官方 API，bbBot 项目自身也在用）。</p>
 */
@Slf4j
@Component
public class Splatoon3CoopTool {

    private static final String SCHEDULES_URL = "https://splatoon3.ink/data/schedules.json";

    @AiTool(
            name = "splatoon3_salmon_run",
            description = "查询 Splatoon3 打工模式（Salmon Run）当前 + 后续时段的地图和支给武器。" +
                    "用户问「打工 / 鲑鱼跑 / salmon run / 现在什么图什么武器」时调本工具。" +
                    "比通用 http_fetch + LLM 解析 schedules.json 更准、更快、token 更少。" +
                    "参数 limit 控制返回多少个时段（默认 2，最多 5）。"
    )
    public Map<String, Object> getCurrentRotation(
            @AiToolParam(name = "limit", description = "返回多少个时段（默认 2，最多 5）", required = false)
            Integer limit
    ) {
        int cap = limit == null || limit <= 0 ? 2 : Math.min(limit, 5);
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(SCHEDULES_URL))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "bbBot-agent")
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                result.put("error", "upstream_http_" + resp.statusCode());
                return result;
            }
            JSONObject root = JSON.parseObject(resp.body());
            JSONObject data = root.getJSONObject("data");
            if (data == null) {
                result.put("error", "no_data_field");
                return result;
            }
            JSONObject coopGrouping = data.getJSONObject("coopGroupingSchedule");
            if (coopGrouping == null) {
                result.put("error", "no_coopGroupingSchedule");
                return result;
            }
            JSONArray nodes = coopGrouping.getJSONObject("regularSchedules").getJSONArray("nodes");
            if (nodes == null || nodes.isEmpty()) {
                result.put("error", "no_schedule_nodes");
                return result;
            }

            List<Map<String, Object>> shifts = new ArrayList<>();
            for (int i = 0; i < Math.min(cap, nodes.size()); i++) {
                JSONObject n = nodes.getJSONObject(i);
                JSONObject setting = n.getJSONObject("setting");
                if (setting == null) continue;
                Map<String, Object> shift = new LinkedHashMap<>();
                shift.put("startTime", n.getString("startTime"));
                shift.put("endTime", n.getString("endTime"));
                // 地图
                JSONObject stage = setting.getJSONObject("coopStage");
                if (stage != null) {
                    shift.put("stage", stage.getString("name"));
                }
                // 武器（4 把）
                JSONArray weapons = setting.getJSONArray("weapons");
                List<String> weaponNames = new ArrayList<>();
                if (weapons != null) {
                    for (int w = 0; w < weapons.size(); w++) {
                        JSONObject ww = weapons.getJSONObject(w);
                        if (ww != null) weaponNames.add(ww.getString("name"));
                    }
                }
                shift.put("weapons", weaponNames);
                // boss
                JSONObject boss = setting.getJSONObject("boss");
                if (boss != null) {
                    shift.put("boss", boss.getString("name"));
                }
                shifts.add(shift);
            }
            result.put("source", SCHEDULES_URL);
            result.put("count", shifts.size());
            result.put("shifts", shifts);
            return result;
        } catch (Exception e) {
            log.warn("splatoon3_salmon_run 失败", e);
            result.put("error", "fetch_failed");
            result.put("message", e.getMessage());
            return result;
        }
    }
}
