package com.bb.bot.controller;

import com.bb.bot.schedule.DailyNewsSchedule;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 每日资讯日报管理端点。
 *
 * <p>提供手动触发一次完整生成流程的入口，便于上线前试跑、或临时补一期。
 * 调用 {@link DailyNewsSchedule#generateNow()}，<b>不受 {@code news.enabled} 开关影响</b>，
 * 即定时关闭时仍可手动触发。同步执行，请求会阻塞至流程完成（含网络采集与可选 LLM）。</p>
 */
@Slf4j
@RestController
@Api(tags = "每日资讯日报-管理")
@RequestMapping("/news")
public class NewsAdminController {

    @Autowired
    private DailyNewsSchedule dailyNewsSchedule;

    /**
     * 手动触发生成一次日报。成功返回访问 URL；无新增内容返回提示；异常返回 500 + 原因。
     */
    @PostMapping(value = "/run", produces = "text/plain;charset=UTF-8")
    @ApiOperation("手动触发生成一次日报")
    public ResponseEntity<String> run() {
        log.info("[news] 收到手动触发请求");
        try {
            String url = dailyNewsSchedule.generateNow();
            if (url == null) {
                return ResponseEntity.ok("已执行：本次无采集内容或无新增条目，未出页");
            }
            return ResponseEntity.ok("生成成功：" + url);
        } catch (Exception e) {
            log.error("[news] 手动触发生成失败", e);
            return ResponseEntity.status(500).body("生成失败：" + e.getMessage());
        }
    }
}
