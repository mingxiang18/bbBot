package com.bb.bot.handler.news.hosting;

import com.bb.bot.config.NewsConfig;
import com.bb.bot.handler.news.contract.NewsHosting;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * {@link NewsHosting} 的落盘实现。
 *
 * <p>把日报 HTML 写到 {@code newsConfig.hosting.dir} 下：当日页 {@code {date}.html}、
 * 归档索引 {@code archive.html}、latest 入口 {@code index.html}（内容即当天页，复制而非软链）。
 * 返回当日页对外访问 URL（{@code publicBase}/{date}.html）。</p>
 *
 * @author ren
 */
@Component
public class NewsHostingImpl implements NewsHosting {

    @Autowired
    private NewsConfig newsConfig;

    @Override
    public String publish(String date, String dailyHtml, String archiveIndexHtml) {
        NewsConfig.Hosting hosting = newsConfig.getHosting();
        Path dir = Paths.get(hosting.getDir());
        try {
            Files.createDirectories(dir);
            // 当日页
            writeUtf8(dir.resolve(date + ".html"), dailyHtml);
            // 归档索引
            writeUtf8(dir.resolve("archive.html"), archiveIndexHtml);
            // latest 入口：复制当天内容到 index.html
            writeUtf8(dir.resolve("index.html"), dailyHtml);
        } catch (IOException e) {
            throw new UncheckedIOException("发布每日日报失败：" + date, e);
        }
        // 拼接对外访问 URL，避免 publicBase 尾部斜杠导致双斜杠
        String base = hosting.getPublicBase();
        if (base != null && base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + date + ".html";
    }

    private void writeUtf8(Path path, String content) throws IOException {
        Files.write(path, (content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
    }
}
