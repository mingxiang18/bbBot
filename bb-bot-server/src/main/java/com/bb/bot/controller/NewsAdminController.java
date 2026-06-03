package com.bb.bot.controller;

import com.bb.bot.config.NewsConfig;
import com.bb.bot.schedule.DailyNewsSchedule;
import com.bb.bot.schedule.NewsGenerationBusyException;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.function.LongSupplier;

/**
 * 每日资讯日报管理端点。
 *
 * <p>提供手动触发一次完整生成流程的入口，便于上线前试跑、或临时补一期。
 * 调用 {@link DailyNewsSchedule#generateNow()}，<b>不受 {@code news.enabled} 开关影响</b>，
 * 即定时关闭时仍可手动触发。同步执行，请求会阻塞至流程完成（含网络采集与可选 LLM）。</p>
 *
 * <p>安全：① <b>令牌鉴权</b>——需携带与 {@code news.admin.token} 一致的令牌（query {@code token}
 * 或 header {@code X-News-Token}）；令牌未配置时 fail-closed（一律 403），避免裸暴露。
 * ② <b>限流</b>——两次手动触发间隔需大于 {@code news.admin.rateLimitMillis}（默认 10 分钟），否则 429。
 * ③ <b>互斥</b>——已有生成任务在执行时返回 409（由 {@link NewsGenerationBusyException} 触发）。</p>
 */
@Slf4j
@RestController
@Api(tags = "每日资讯日报-管理")
@RequestMapping("/news")
public class NewsAdminController {

    @Autowired
    private DailyNewsSchedule dailyNewsSchedule;

    @Autowired
    private NewsConfig newsConfig;

    /** 当前时间源，便于单测注入。 */
    private LongSupplier nowSupplier = System::currentTimeMillis;

    /** 最近一次成功通过鉴权并触发的时间戳（毫秒）；0 表示从未触发。 */
    private volatile long lastRunAt = 0L;

    /**
     * 手动触发生成一次日报。
     *
     * @return 200 成功/短路；403 未授权；429 触发过于频繁；409 已有任务在执行；500 生成异常
     */
    @PostMapping(value = "/run", produces = "text/plain;charset=UTF-8")
    @ApiOperation("手动触发生成一次日报")
    public ResponseEntity<String> run(
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "X-News-Token", required = false) String headerToken) {

        // 1) 鉴权（fail-closed）
        String expected = newsConfig.getAdmin() == null ? null : newsConfig.getAdmin().getToken();
        if (StringUtils.isBlank(expected)) {
            log.warn("[news] /news/run 未配置 news.admin.token，拒绝触发");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("未配置触发令牌（news.admin.token），端点已禁用");
        }
        String provided = StringUtils.firstNonBlank(token, headerToken);
        if (!constantTimeEquals(expected, provided)) {
            log.warn("[news] /news/run 令牌校验失败");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("无权触发：令牌无效");
        }

        // 2) 限流
        long now = nowSupplier.getAsLong();
        long window = newsConfig.getAdmin().getRateLimitMillis();
        long since = now - lastRunAt;
        if (lastRunAt > 0 && window > 0 && since < window) {
            long waitSec = (window - since) / 1000;
            log.info("[news] /news/run 触发过于频繁，剩余冷却 {} 秒", waitSec);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("触发过于频繁，请约 " + waitSec + " 秒后再试");
        }
        lastRunAt = now;

        // 3) 生成（互斥 + 异常映射）
        log.info("[news] 收到手动触发请求（鉴权通过）");
        try {
            String url = dailyNewsSchedule.generateNow();
            if (url == null) {
                return ResponseEntity.ok("已执行：本次无采集内容/无新增/空精选，未出页");
            }
            return ResponseEntity.ok("生成成功：" + url);
        } catch (NewsGenerationBusyException busy) {
            log.info("[news] 生成被互斥拒绝：{}", busy.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(busy.getMessage());
        } catch (Exception e) {
            log.error("[news] 手动触发生成失败", e);
            return ResponseEntity.status(500).body("生成失败：" + e.getMessage());
        }
    }

    /** 定长比较，避免令牌校验的时序侧信道。 */
    private static boolean constantTimeEquals(String expected, String provided) {
        if (expected == null || provided == null) {
            return false;
        }
        byte[] a = expected.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] b = provided.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(a, b);
    }
}
