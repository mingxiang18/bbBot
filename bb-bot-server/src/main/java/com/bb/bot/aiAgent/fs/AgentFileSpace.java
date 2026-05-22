package com.bb.bot.aiAgent.fs;

import com.bb.bot.aiAgent.tools.MemoryToolContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/**
 * 每用户文件空间：解析「调用者自己的目录」并做越权校验。
 *
 * <p>每个用户的文件落在 {@code {userFileRoot}/{safe(userId)}/} 子树下。agent 的文件
 * 工具（file_read / file_write / list_dir）与入站附件落盘（{@link AgentFileStore}）
 * 都经由本类解析路径，确保用户之间互不可见。</p>
 *
 * <p>当前调用者 id 取自 {@link MemoryToolContext} 的 ThreadLocal —— AiToolExecutor
 * 在工具线程上反射调用前已把 caller user id 放进去。</p>
 */
@Slf4j
@Component
public class AgentFileSpace {

    @Value("${aiAgent.fs.userFileRoot:./agent-files}")
    private String userFileRoot;

    /** 指定用户的根目录，绝对化 + 正规化。 */
    public Path userRoot(String userId) {
        return Paths.get(userFileRoot, safe(userId)).toAbsolutePath().normalize();
    }

    /** 0777：bot 以 root 跑、bb-sandbox 以 uid 1000 跑，二者共享 hostPath，需同目录互相可写。 */
    private static final Set<PosixFilePermission> SHARED_DIR_PERMS =
            PosixFilePermissions.fromString("rwxrwxrwx");

    /**
     * 确保用户根目录存在且对共享卷上的另一 uid 可写（shell_exec 沙箱把该目录当 /work，
     * 需要能在里面写产物）。bot 是 owner（root），chmod 0777 由它完成；非 POSIX 文件系统忽略。
     */
    public Path ensureSharedUserDir(String userId) {
        Path root = userRoot(userId);
        try {
            Files.createDirectories(root);
            try {
                Files.setPosixFilePermissions(root, SHARED_DIR_PERMS);
            } catch (UnsupportedOperationException ignore) {
                // 非 POSIX FS（如本地 Windows 开发）：忽略
            }
        } catch (IOException e) {
            log.warn("ensureSharedUserDir 失败 user={}", userId, e);
        }
        return root;
    }

    /**
     * 把工具传入的 path 解析到「指定用户目录」内并校验越权。
     *
     * <p>相对路径相对该用户根目录解析；绝对路径直接采用。正规化（消解 {@code ../}）
     * 后必须落在用户根目录子树内，否则抛 {@link PathEscapeException}。这是防越权的
     * 唯一闸口。</p>
     */
    public Path resolveWithin(String userId, String path) {
        Path root = userRoot(userId);
        String raw = path == null ? "" : path.trim();
        Path candidate = raw.isEmpty() ? root : Paths.get(raw);
        Path resolved = (candidate.isAbsolute() ? candidate : root.resolve(candidate))
                .toAbsolutePath().normalize();
        if (!resolved.startsWith(root)) {
            throw new PathEscapeException(resolved, root);
        }
        return resolved;
    }

    /** {@link #resolveWithin} 的当前调用者版本。 */
    public Path resolveForCurrentUser(String path) {
        return resolveWithin(MemoryToolContext.getUserId(), path);
    }

    /** 把 user id 清洗成安全的目录名（与 ExperienceStore.safe 一致）。 */
    public static String safe(String id) {
        if (id == null || id.isEmpty()) {
            return "_anonymous";
        }
        return id.replaceAll("[^\\w\\-]", "_");
    }

    /** 路径越权异常：解析结果跑出了用户根目录。 */
    public static class PathEscapeException extends RuntimeException {
        public PathEscapeException(Path resolved, Path root) {
            super("path escapes user root: " + resolved + " not under " + root);
        }
    }
}
