package com.bb.bot.aiAgent.sandbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 启动期探测可用后端并暴露一个 {@link #current()}。优先级：bubblewrap > docker > noop。
 *
 * <p>可用 {@code aiAgent.sandbox.preferred} 强制选择某个后端。</p>
 */
@Slf4j
@Component
public class SandboxRunnerFactory {

    @Autowired
    private BubblewrapSandboxRunner bwrap;

    @Autowired
    private DockerExecSandboxRunner docker;

    @Autowired
    private NoOpSandboxRunner noop;

    @Value("${aiAgent.sandbox.preferred:auto}")
    private String preferred;

    private SandboxRunner current;

    @PostConstruct
    public void init() {
        current = select();
        log.info("AI Agent 沙箱后端: {}（preferred={}）", current.backendName(), preferred);
    }

    private SandboxRunner select() {
        switch (preferred.toLowerCase()) {
            case "bubblewrap":
                return bwrap.available() ? bwrap : noop;
            case "docker":
                return docker.available() ? docker : noop;
            case "noop":
                return noop;
            default: // auto
                if (bwrap.available()) return bwrap;
                if (docker.available()) return docker;
                return noop;
        }
    }

    public SandboxRunner current() {
        return current;
    }
}
