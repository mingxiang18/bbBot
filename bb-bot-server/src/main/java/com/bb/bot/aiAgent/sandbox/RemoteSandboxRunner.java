package com.bb.bot.aiAgent.sandbox;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.aiAgent.tools.MemoryToolContext;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 远程沙箱后端：把命令 HTTP POST 给独立的 bb-sandbox pod 执行。
 *
 * <p>bot 容器自身不跑代码、不装 bwrap、不放开安全策略；执行与 bwrap 隔离都发生在 sandbox pod。
 * 协议只传 {@code userId + command + timeout}，由 sandbox 端在自己的挂载点（共享 NFS）解析用户目录，
 * 不泄漏 bot 侧绝对路径。userId 取自 {@link MemoryToolContext}（runner 在工具线程同步执行）。</p>
 *
 * <p>用独立的 httpclient5 实例：<b>不复用</b> {@code RestClientConfig} 的 RestClient——后者注入了外网代理
 * （rest.proxyIp），会把集群内调用打到代理。</p>
 */
@Slf4j
@Component
public class RemoteSandboxRunner implements SandboxRunner {

    @Value("${aiAgent.sandbox.remote.url:}")
    private String baseUrl;

    @Value("${aiAgent.sandbox.remote.authToken:}")
    private String authToken;

    @Value("${aiAgent.sandbox.remote.connectTimeoutMs:3000}")
    private int connectTimeoutMs;

    @Value("${aiAgent.sandbox.remote.readTimeoutMs:60000}")
    private int readTimeoutMs;

    private CloseableHttpClient httpClient;

    @PostConstruct
    public void init() {
        // connectTimeout 在 hc5 5.2 走 ConnectionManager 的 ConnectionConfig（RequestConfig.setConnectTimeout 已废弃）
        ConnectionConfig connConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(connectTimeoutMs))
                .build();
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setDefaultConnectionConfig(connConfig);
        RequestConfig rc = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(connectTimeoutMs))
                .setResponseTimeout(Timeout.ofMilliseconds(readTimeoutMs))
                .build();
        this.httpClient = HttpClients.custom()
                .setConnectionManager(cm)
                .setDefaultRequestConfig(rc)
                .build();
        if (available()) {
            log.info("RemoteSandboxRunner: 已配置 url={}", baseUrl);
        }
    }

    @PreDestroy
    public void close() {
        if (httpClient != null) {
            try {
                httpClient.close();
            } catch (Exception ignore) {
            }
        }
    }

    @Override
    public boolean available() {
        return StringUtils.isNotBlank(baseUrl);
    }

    @Override
    public String backendName() {
        return "remote";
    }

    @Override
    public SandboxResult run(SandboxSpec spec, String[] command, Path workdir) {
        Instant start = Instant.now();
        String shell = extractShell(command);

        JSONObject req = new JSONObject();
        req.put("userId", MemoryToolContext.getUserId());
        req.put("command", shell);
        req.put("timeoutMs", spec.getTimeout() == null ? 15000L : spec.getTimeout().toMillis());
        req.put("networkEnabled", spec.isNetworkEnabled());

        try {
            HttpPost post = new HttpPost(baseUrl + "/exec");
            post.setEntity(new StringEntity(JSON.toJSONString(req), ContentType.APPLICATION_JSON));
            if (StringUtils.isNotBlank(authToken)) {
                post.setHeader("X-Sandbox-Token", authToken);
            }
            String text = httpClient.execute(post, resp -> {
                String b = resp.getEntity() == null ? ""
                        : EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
                if (resp.getCode() != 200) {
                    throw new java.io.IOException("sandbox http " + resp.getCode() + ": " + cap(b));
                }
                return b;
            });
            JSONObject json = JSON.parseObject(text);
            return new SandboxResult(
                    json.getIntValue("exitCode", -1),
                    StringUtils.defaultString(json.getString("stdout")),
                    StringUtils.defaultString(json.getString("stderr")),
                    Duration.ofMillis(json.getLongValue("durationMs", elapsedMs(start))),
                    json.getBooleanValue("timedOut", false));
        } catch (Exception e) {
            log.warn("远程沙箱调用失败 url={}", baseUrl, e);
            return new SandboxResult(-1, "", "sandbox unreachable: " + e.getMessage(),
                    Duration.ofMillis(elapsedMs(start)), false);
        }
    }

    /** ShellExecTool 固定传 ["bash","-c",cmd]；取真正的 shell 串，其它形态退化为空格拼接。 */
    private String extractShell(String[] command) {
        if (command == null || command.length == 0) {
            return "";
        }
        if (command.length >= 3
                && ("bash".equals(command[0]) || "sh".equals(command[0]))
                && "-c".equals(command[1])) {
            return command[2];
        }
        return String.join(" ", command);
    }

    private long elapsedMs(Instant start) {
        return Duration.between(start, Instant.now()).toMillis();
    }

    private String cap(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 512 ? s : s.substring(0, 512) + "...";
    }
}
