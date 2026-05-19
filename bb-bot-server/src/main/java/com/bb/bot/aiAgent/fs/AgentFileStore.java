package com.bb.bot.aiAgent.fs;

import com.bb.bot.common.util.RestUtils;
import com.bb.bot.constant.BbSendMessageType;
import com.bb.bot.entity.bb.BbMessageContent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

/**
 * 把 bb 协议入站附件（文件 / 图片）落盘到调用者的每用户目录。
 *
 * <p>文件（localFile / netFile）落盘后，把 content 的 {@code data} 改写成本地绝对
 * 路径，这样 {@code MessageBuilder} 能把路径告诉模型、模型再用 file_read 读取。
 * 图片（localImage / netImage）也落盘存档一份，但不改 {@code data} —— 保留
 * base64 / URL 让 vision 链路继续工作。</p>
 */
@Slf4j
@Component
public class AgentFileStore {

    /** 单个附件下载 / 解码上限，避免塞爆磁盘。 */
    private static final long MAX_BYTES = 20L * 1024 * 1024;

    @Autowired
    private AgentFileSpace fileSpace;

    /** 走项目统一的 RestClient（带代理），否则国内抓不到外网附件。 */
    @Autowired
    private RestUtils restUtils;

    /**
     * 落盘当前轮入站附件。直接原地修改传入列表里文件类附件的 {@code data}。
     *
     * @param userId    调用者 user id（决定落盘到哪个用户目录）
     * @param messageId 本轮消息 id（用作 inbound 子目录，隔离不同消息）
     * @param contents  本轮消息内容；文件类附件的 data 会被改写为本地路径
     */
    public void materializeInbound(String userId, String messageId, List<BbMessageContent> contents) {
        if (contents == null || contents.isEmpty()) {
            return;
        }
        Path inboundDir = fileSpace.userRoot(userId)
                .resolve("inbound")
                .resolve(AgentFileSpace.safe(messageId));
        for (BbMessageContent c : contents) {
            String type = c.getType();
            boolean isFile = BbSendMessageType.LOCAL_FILE.equals(type) || BbSendMessageType.NET_FILE.equals(type);
            boolean isImage = BbSendMessageType.LOCAL_IMAGE.equals(type) || BbSendMessageType.NET_IMAGE.equals(type);
            if (!isFile && !isImage) {
                continue;
            }
            try {
                Path saved = saveOne(inboundDir, c);
                if (saved != null && isFile) {
                    // 文件类：把 data 换成本地路径，供 MessageBuilder 暴露给模型
                    c.setData(saved.toString());
                }
                // 图片类：落盘存档即可，data 保持不变（base64 / URL 继续喂 vision）
            } catch (Exception e) {
                log.warn("入站附件落盘失败 type={} fileName={}", type, c.getFileName(), e);
            }
        }
    }

    private Path saveOne(Path dir, BbMessageContent c) throws Exception {
        byte[] bytes = readBytes(c);
        if (bytes == null) {
            return null;
        }
        if (bytes.length > MAX_BYTES) {
            log.warn("入站附件超过 {} 字节上限，跳过 fileName={}", MAX_BYTES, c.getFileName());
            return null;
        }
        Files.createDirectories(dir);
        Path target = uniquePath(dir, sanitizeFileName(c));
        Files.write(target, bytes);
        return target;
    }

    /** 取出附件字节：localXxx 的 data 是 base64，netXxx 的 data 是下载 URL。 */
    private byte[] readBytes(BbMessageContent c) throws Exception {
        String type = c.getType();
        Object data = c.getData();
        if (data == null) {
            return null;
        }
        if (BbSendMessageType.LOCAL_FILE.equals(type) || BbSendMessageType.LOCAL_IMAGE.equals(type)) {
            String b64 = String.valueOf(data);
            int comma = b64.indexOf(',');
            if (b64.startsWith("data:") && comma > 0) {
                b64 = b64.substring(comma + 1);  // 去掉 data:*;base64, 前缀
            }
            return Base64.getDecoder().decode(b64);
        }
        // netFile / netImage：URL 下载
        String url = String.valueOf(data);
        if (isInternalHost(URI.create(url).getHost())) {
            log.warn("入站附件 URL 指向内网，拒绝下载 url={}", url);
            return null;
        }
        try (InputStream in = restUtils.getFileInputStream(url)) {
            return in.readAllBytes();
        }
    }

    /** 文件名只取 basename 并清洗，杜绝 {@code ../} 与路径分隔符。 */
    private String sanitizeFileName(BbMessageContent c) {
        String name = c.getFileName();
        if (StringUtils.isBlank(name)) {
            name = "attachment";
        }
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[^\\w.\\-]", "_");
        if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
            name = "attachment";
        }
        return name;
    }

    /** 同名文件已存在时追加 -1 / -2 …，避免一轮里多个同名附件互相覆盖。 */
    private Path uniquePath(Path dir, String name) {
        Path p = dir.resolve(name);
        if (!Files.exists(p)) {
            return p;
        }
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; i < 1000; i++) {
            Path cand = dir.resolve(base + "-" + i + ext);
            if (!Files.exists(cand)) {
                return cand;
            }
        }
        return dir.resolve(base + "-" + System.nanoTime() + ext);
    }

    /** SSRF 防护：内网地址一律拒绝（同 HttpFetchTool 思路）。 */
    private boolean isInternalHost(String host) {
        if (host == null || host.isEmpty()) {
            return true;
        }
        try {
            InetAddress addr = InetAddress.getByName(host);
            return addr.isLoopbackAddress() || addr.isSiteLocalAddress()
                    || addr.isAnyLocalAddress() || addr.isLinkLocalAddress();
        } catch (Exception e) {
            return true;
        }
    }
}
