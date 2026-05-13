package com.bb.bot.aiAgent.tools;

import com.bb.bot.aiAgent.core.AiTool;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 演示工具：返回服务器当前时间。无副作用、无参数，可被任何用户调用。
 *
 * <p>主要价值：M2 启动后验证 function calling 链路通畅 —— 问 AI「现在几点」时
 * 它应自动调用本工具并把结果嵌回回复。</p>
 */
@Component
public class ServerTimeTool {

    @AiTool(
            name = "server_time",
            description = "查询服务器当前时间。当用户询问现在几点 / 几月几号 / 现在的时间时调用本工具。"
    )
    public Map<String, Object> getServerTime() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("iso", now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        result.put("zone", now.getZone().getId());
        result.put("dayOfWeek", now.getDayOfWeek().name());
        return result;
    }
}
