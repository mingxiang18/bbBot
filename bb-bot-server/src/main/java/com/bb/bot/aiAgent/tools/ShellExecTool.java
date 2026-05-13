package com.bb.bot.aiAgent.tools;

import com.bb.bot.aiAgent.core.AiTool;
import com.bb.bot.aiAgent.core.AiToolParam;
import com.bb.bot.aiAgent.sandbox.SandboxResult;
import com.bb.bot.aiAgent.sandbox.SandboxRunner;
import com.bb.bot.aiAgent.sandbox.SandboxRunnerFactory;
import com.bb.bot.aiAgent.sandbox.SandboxSpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 高危工具：在沙箱里跑一个 shell 命令。
 *
 * <p>设计选择：</p>
 * <ul>
 *   <li>仅 owner 可调（{@code requiresOwner=true}），策略表里也不应给普通角色开</li>
 *   <li>requires_sandbox=true 给 future-AiToolExecutor 拒绝裸机调用</li>
 *   <li>固定走 bash -c，避免参数注入路径分歧</li>
 *   <li>默认禁网络（spec.networkEnabled=false），AI 要走网络应用 http_fetch 工具</li>
 *   <li>15s 硬超时（沙箱再加一层）</li>
 *   <li>stdout 截断 8KB 回给 LLM</li>
 * </ul>
 */
@Slf4j
@Component
public class ShellExecTool {

    @Autowired
    private SandboxRunnerFactory factory;

    @AiTool(
            name = "shell_exec",
            description = "在隔离沙箱里执行一个 bash 命令。仅 owner 可调。" +
                    "默认无网络、15s 超时、stdout 上限 8KB。" +
                    "用于：跑脚本、检查文件、运行小工具。绝不要用于持续运行的服务。",
            requiresOwner = true,
            requiresSandbox = true
    )
    public Map<String, Object> exec(
            @AiToolParam(name = "command", description = "要执行的 bash 命令（单条，参数空格分隔）")
            String command
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        SandboxRunner runner = factory.current();
        result.put("backend", runner.backendName());
        if ("noop".equals(runner.backendName())) {
            result.put("error", "sandbox_unavailable");
            return result;
        }
        SandboxSpec spec = SandboxSpec.builder()
                .networkEnabled(false)
                .timeout(Duration.ofSeconds(15))
                .build();
        SandboxResult sr = runner.run(spec, new String[]{"bash", "-c", command}, Path.of("/tmp"));
        result.put("exitCode", sr.getExitCode());
        result.put("durationMs", sr.getDuration().toMillis());
        result.put("timedOut", sr.isTimedOut());
        result.put("stdout", cap(sr.getStdout(), 8192));
        result.put("stderr", cap(sr.getStderr(), 4096));
        return result;
    }

    private String cap(String s, int limit) {
        if (s == null) return "";
        if (s.length() <= limit) return s;
        return s.substring(0, limit) + "...[truncated]";
    }
}
