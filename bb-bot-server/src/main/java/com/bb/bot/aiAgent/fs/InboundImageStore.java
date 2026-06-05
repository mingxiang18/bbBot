package com.bb.bot.aiAgent.fs;

import com.bb.bot.common.util.HashUtil;
import com.bb.bot.common.util.RestUtils;
import com.bb.bot.constant.BbSendMessageType;
import com.bb.bot.entity.bb.BbMessageContent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

/**
 * 入站图片规范化：把消息里的 localImage(base64) / netImage(URL) 按内容哈希落盘去重，
 * 并把该 part 改写成 {@code netImage(ref)}（data=/img/&lt;hash&gt;.png，fileName=hash）。
 *
 * <p>这样历史里每张图只留一个稳定 ref/链接（localImage 会被 MemoryEventRecorder 过滤，
 * netImage 则保留），AI 看到 ref 后经 {@code analyze_image(ref)} 读本地字节按需识图。
 * 同一张图（同 sha256(bytes)）只落盘一份。</p>
 *
 * <p>必须在 recordInbound / 事件分发之前调用（见 {@code BbEventListener}）。</p>
 *
 * @author ren
 */
@Slf4j
@Component
public class InboundImageStore {

    private static final long MAX_BYTES = 20L * 1024 * 1024;

    /** 可选：拼成可访问 URL 的前缀（如 http://bb-bot:8099）。留空则 ref 用相对路径 /img/&lt;hash&gt;.png。 */
    @Value("${aiAgent.img.baseUrl:}")
    private String imgBaseUrl;

    @Autowired
    private AgentFileSpace fileSpace;

    @Autowired
    private RestUtils restUtils;

    /** 原地规范化 contents 里的图片 part：落盘去重 + 改写成 netImage(ref)。 */
    public void normalize(List<BbMessageContent> contents) {
        if (contents == null || contents.isEmpty()) {
            return;
        }
        for (BbMessageContent c : contents) {
            String type = c.getType();
            if (!BbSendMessageType.LOCAL_IMAGE.equals(type) && !BbSendMessageType.NET_IMAGE.equals(type)) {
                continue;
            }
            try {
                byte[] bytes = readBytes(c);
                if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
                    continue;
                }
                String hash = HashUtil.sha256Hex(bytes);
                Path target = pathForHash(hash);
                if (!Files.exists(target)) {
                    Files.createDirectories(target.getParent());
                    Files.write(target, bytes);
                }
                c.setType(BbSendMessageType.NET_IMAGE);
                c.setData(refUrl(hash));
                c.setFileName(hash);
            } catch (Exception e) {
                log.warn("入站图片规范化失败 type={} fileName={}", c.getType(), c.getFileName(), e);
            }
        }
    }

    /** analyze_image 用：把 ref（hash / /img/&lt;hash&gt;.png / 完整 URL）解析为本地图片字节。 */
    public byte[] bytesForRef(String ref) {
        String hash = extractHash(ref);
        if (hash == null) {
            return null;
        }
        try {
            Path p = pathForHash(hash);
            if (Files.exists(p)) {
                return Files.readAllBytes(p);
            }
        } catch (Exception e) {
            log.warn("读取图片缓存失败 ref={}", ref, e);
        }
        return null;
    }

    /** 从 ref 抽出 hex 哈希。 */
    public String extractHash(String ref) {
        if (StringUtils.isBlank(ref)) {
            return null;
        }
        String s = ref.trim();
        int slash = s.lastIndexOf('/');
        if (slash >= 0) {
            s = s.substring(slash + 1);
        }
        int dot = s.indexOf('.');
        if (dot > 0) {
            s = s.substring(0, dot);
        }
        if (s.matches("[a-fA-F0-9]{16,80}")) {
            return s.toLowerCase();
        }
        return null;
    }

    /** 全局图片缓存目录（按哈希去重、跨用户共享）。 */
    private Path pathForHash(String hash) {
        return fileSpace.userRoot("_imgcache").resolve(hash + ".png");
    }

    private String refUrl(String hash) {
        String base = imgBaseUrl == null ? "" : imgBaseUrl.replaceAll("/+$", "");
        return base + "/img/" + hash + ".png";
    }

    private byte[] readBytes(BbMessageContent c) throws Exception {
        Object data = c.getData();
        if (data == null) {
            return null;
        }
        if (BbSendMessageType.LOCAL_IMAGE.equals(c.getType())) {
            String b64 = String.valueOf(data);
            int comma = b64.indexOf(',');
            if (b64.startsWith("data:") && comma > 0) {
                b64 = b64.substring(comma + 1);
            }
            return Base64.getDecoder().decode(b64);
        }
        String url = String.valueOf(data);
        // 已经是我们自己的 ref(/img/...) → 不重复处理
        if (url.contains("/img/") && extractHash(url) != null) {
            return null;
        }
        if (isInternalHost(URI.create(url).getHost())) {
            log.warn("入站图片 URL 指向内网，拒绝下载 url={}", url);
            return null;
        }
        try (InputStream in = restUtils.getFileInputStream(url)) {
            return in.readAllBytes();
        }
    }

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
