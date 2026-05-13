package com.bb.bot.aiAgent.sandbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Bubblewrap 后端。要求 host 装了 {@code bwrap}。
 *
 * <p>不依赖 root（user namespace），启动开销小，但只能在 Linux 用。</p>
 *
 * <p>默认挂载：/usr / /bin / /lib / /lib64 只读，/etc 只读（解析 /etc/resolv.conf 等需要），
 * spec 额外指定的挂载点叠加在后面。/proc 和 /dev 暴露 tmpfs。</p>
 */
@Slf4j
@Component
public class BubblewrapSandboxRunner implements SandboxRunner {

    private static final String BWRAP = "bwrap";
    private final boolean available;

    public BubblewrapSandboxRunner() {
        this.available = probe();
        if (available) {
            log.info("BubblewrapSandboxRunner: bwrap 可用");
        } else {
            log.debug("BubblewrapSandboxRunner: bwrap 不可用（OS={}）", System.getProperty("os.name"));
        }
    }

    private boolean probe() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("linux")) {
            return false;
        }
        try {
            Process p = new ProcessBuilder(BWRAP, "--version").redirectErrorStream(true).start();
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
        return "bubblewrap";
    }

    @Override
    public SandboxResult run(SandboxSpec spec, String[] command, Path workdir) {
        Instant start = Instant.now();
        List<String> cmd = new ArrayList<>();
        cmd.add(BWRAP);
        // 标准最小化挂载
        for (String p : new String[]{"/usr", "/bin", "/lib", "/lib64", "/etc"}) {
            java.io.File f = new java.io.File(p);
            if (f.exists()) {
                cmd.add("--ro-bind");
                cmd.add(p);
                cmd.add(p);
            }
        }
        cmd.add("--proc");
        cmd.add("/proc");
        cmd.add("--dev");
        cmd.add("/dev");
        cmd.add("--tmpfs");
        cmd.add("/tmp");
        // spec 自定义挂载
        if (spec.getReadOnlyMounts() != null) {
            for (SandboxSpec.Mount m : spec.getReadOnlyMounts()) {
                cmd.add("--ro-bind");
                cmd.add(m.getHostPath().toString());
                cmd.add(m.getContainerPath());
            }
        }
        if (spec.getReadWriteMounts() != null) {
            for (SandboxSpec.Mount m : spec.getReadWriteMounts()) {
                cmd.add("--bind");
                cmd.add(m.getHostPath().toString());
                cmd.add(m.getContainerPath());
            }
        }
        if (!spec.isNetworkEnabled()) {
            cmd.add("--unshare-net");
        }
        if (workdir != null) {
            cmd.add("--chdir");
            cmd.add(workdir.toString());
        }
        cmd.add("--die-with-parent");
        cmd.add("--unshare-user");
        cmd.add("--unshare-pid");
        cmd.add("--unshare-uts");
        cmd.add("--unshare-ipc");
        // 实际命令
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
            log.warn("bwrap 执行异常", e);
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
                    if (sink.length() > 1024 * 64) break; // cap 64KB
                }
            } catch (IOException ignore) {}
        });
        t.setDaemon(true);
        t.start();
        return t;
    }
}
