package com.bb.bot.aiAgent.sandbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Docker exec 后端：每次起一个一次性容器，跑完即 --rm 销毁。
 *
 * <p>启动开销大（~500ms），但隔离比 bwrap 强且跨 Linux / macOS，作为没 bwrap 时的备选。</p>
 *
 * <p>需要 host 装了 docker daemon + 当前用户能调用 docker。默认 image 通过
 * {@code aiAgent.sandbox.dockerImage} 配置，默认 {@code alpine:3.20}。</p>
 */
@Slf4j
@Component
public class DockerExecSandboxRunner implements SandboxRunner {

    private static final String DOCKER = "docker";

    @Value("${aiAgent.sandbox.dockerImage:alpine:3.20}")
    private String image;

    private final boolean available;

    public DockerExecSandboxRunner() {
        this.available = probe();
        if (available) {
            log.info("DockerExecSandboxRunner: docker 可用");
        }
    }

    private boolean probe() {
        try {
            Process p = new ProcessBuilder(DOCKER, "version", "--format", "{{.Server.Version}}")
                    .redirectErrorStream(true).start();
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean available() {
        return available;
    }

    @Override
    public String backendName() {
        return "docker";
    }

    @Override
    public SandboxResult run(SandboxSpec spec, String[] command, Path workdir) {
        Instant start = Instant.now();
        List<String> cmd = new ArrayList<>();
        cmd.add(DOCKER);
        cmd.add("run");
        cmd.add("--rm");
        cmd.add("-i");
        if (!spec.isNetworkEnabled()) {
            cmd.add("--network=none");
        }
        if (spec.getMemoryLimitMb() != null) {
            cmd.add("--memory=" + spec.getMemoryLimitMb() + "m");
        }
        if (spec.getCpuLimitPercent() != null) {
            cmd.add("--cpus=" + (spec.getCpuLimitPercent() / 100.0));
        }
        if (spec.getReadOnlyMounts() != null) {
            for (SandboxSpec.Mount m : spec.getReadOnlyMounts()) {
                cmd.add("-v");
                cmd.add(m.getHostPath().toString() + ":" + m.getContainerPath() + ":ro");
            }
        }
        if (spec.getReadWriteMounts() != null) {
            for (SandboxSpec.Mount m : spec.getReadWriteMounts()) {
                cmd.add("-v");
                cmd.add(m.getHostPath().toString() + ":" + m.getContainerPath());
            }
        }
        if (workdir != null) {
            cmd.add("-w");
            cmd.add(workdir.toString());
        }
        cmd.add(image);
        for (String c : command) {
            cmd.add(c);
        }
        return execute(cmd, spec.getTimeout(), start);
    }

    private SandboxResult execute(List<String> cmd, Duration timeout, Instant start) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            Process process = pb.start();
            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            Thread outReader = drain(process.getInputStream(), stdout);
            Thread errReader = drain(process.getErrorStream(), stderr);
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                outReader.join(1000);
                errReader.join(1000);
                return new SandboxResult(-1, stdout.toString(), stderr.toString(),
                        Duration.between(start, Instant.now()), true);
            }
            outReader.join(2000);
            errReader.join(2000);
            return new SandboxResult(process.exitValue(), stdout.toString(), stderr.toString(),
                    Duration.between(start, Instant.now()), false);
        } catch (IOException | InterruptedException e) {
            log.warn("docker 沙箱执行异常", e);
            return new SandboxResult(-1, "", e.getMessage(),
                    Duration.between(start, Instant.now()), false);
        }
    }

    private Thread drain(java.io.InputStream in, StringBuilder sink) {
        Thread t = new Thread(() -> {
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(in))) {
                String line;
                while ((line = r.readLine()) != null) {
                    sink.append(line).append("\n");
                    if (sink.length() > 1024 * 64) break;
                }
            } catch (IOException ignore) {}
        });
        t.setDaemon(true);
        t.start();
        return t;
    }
}
