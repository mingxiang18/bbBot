package com.bb.bot.aiAgent.sandbox;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 一次沙箱执行的约束声明。子段尽量贴近 Bubblewrap / Docker 都能映射的最小公分母。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SandboxSpec {

    /** 只读挂载（host path → 沙箱内可见路径），常用于把 /usr / /bin / /lib 暴露给子进程 */
    @Builder.Default
    private List<Mount> readOnlyMounts = new ArrayList<>();

    /** 读写挂载，常用于一个 tmp 工作目录 */
    @Builder.Default
    private List<Mount> readWriteMounts = new ArrayList<>();

    /** 是否允许网络。默认禁 */
    @Builder.Default
    private boolean networkEnabled = false;

    /** 最大 wall-clock 时间。沙箱实现负责强杀。 */
    @Builder.Default
    private Duration timeout = Duration.ofSeconds(30);

    /** 内存上限（MB）。null 表示沿用默认。 */
    private Integer memoryLimitMb;

    /** CPU 上限（核数百分比，比如 50 = 0.5 核）。null 表示沿用默认。 */
    private Integer cpuLimitPercent;

    @Data
    @AllArgsConstructor
    public static class Mount {
        private final Path hostPath;
        private final String containerPath;
    }
}
