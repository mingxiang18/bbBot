package com.bb.bot.aiAgent.sandbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;

/**
 * 终极兜底：宿主既没有 bwrap 也没有 docker 时，直接拒绝执行。
 *
 * <p>故意不回退到裸机 ProcessBuilder —— 让高危工具在没有隔离的环境下默默运行
 * 是更严重的事故。Factory 在此情况下会让 ShellExecTool 报「沙箱不可用」给 LLM。</p>
 */
@Slf4j
@Component
public class NoOpSandboxRunner implements SandboxRunner {

    @Override
    public SandboxResult run(SandboxSpec spec, String[] command, Path workdir) {
        log.warn("沙箱不可用，拒绝执行命令: {}", String.join(" ", command));
        return new SandboxResult(-2, "", "sandbox_unavailable", Duration.ZERO, false);
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public String backendName() {
        return "noop";
    }
}
