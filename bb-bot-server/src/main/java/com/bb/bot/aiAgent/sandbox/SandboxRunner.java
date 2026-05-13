package com.bb.bot.aiAgent.sandbox;

import java.nio.file.Path;

/**
 * 把一个命令丢进沙箱执行。
 *
 * <p>三种实现：</p>
 * <ul>
 *   <li>{@link BubblewrapSandboxRunner} —— Linux 主选，启动 50ms 量级</li>
 *   <li>{@link DockerExecSandboxRunner} —— 备选，启动 500ms 量级，但隔离更强</li>
 *   <li>{@link NoOpSandboxRunner} —— 都不可用时拒绝执行（不会回退到裸机！）</li>
 * </ul>
 *
 * <p>选择由 {@link SandboxRunnerFactory} 启动时探测决定。</p>
 */
public interface SandboxRunner {

    /**
     * 在沙箱里跑一个命令。
     *
     * @param spec     沙箱约束
     * @param command  命令 + 参数（不会经过 shell，避免注入；如需 shell 必须显式 ["bash","-c","..."]）
     * @param workdir  沙箱内 cwd（必须在 readWriteMounts 之一里）
     * @return         执行结果（包含 stdout / stderr / exit code）
     */
    SandboxResult run(SandboxSpec spec, String[] command, Path workdir);

    /** 这个实现是不是已就绪可用（host 上有对应工具）。 */
    boolean available();

    /** 用于日志 / debug 标识。 */
    String backendName();
}
