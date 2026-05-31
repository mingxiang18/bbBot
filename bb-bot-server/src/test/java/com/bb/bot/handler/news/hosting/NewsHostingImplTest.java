package com.bb.bot.handler.news.hosting;

import com.bb.bot.config.NewsConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class NewsHostingImplTest {

    @TempDir
    Path tempDir;

    private NewsHostingImpl newHosting(String dir, String publicBase) {
        NewsConfig newsConfig = new NewsConfig();
        newsConfig.getHosting().setDir(dir);
        newsConfig.getHosting().setPublicBase(publicBase);
        NewsHostingImpl impl = new NewsHostingImpl();
        ReflectionTestUtils.setField(impl, "newsConfig", newsConfig);
        return impl;
    }

    @Test
    void publish_writesThreeFilesAndReturnsUrl() throws Exception {
        NewsHostingImpl impl = newHosting(tempDir.toString(), "/news");
        String date = "2026-05-30";
        String dailyHtml = "<html><body>今日 " + date + "</body></html>";
        String archiveHtml = "<html><body>归档索引</body></html>";

        String url = impl.publish(date, dailyHtml, archiveHtml);

        Path dailyFile = tempDir.resolve(date + ".html");
        Path indexFile = tempDir.resolve("index.html");
        Path archiveFile = tempDir.resolve("archive.html");

        assertThat(dailyFile).exists();
        assertThat(indexFile).exists();
        assertThat(archiveFile).exists();

        assertThat(Files.readString(dailyFile, StandardCharsets.UTF_8)).isEqualTo(dailyHtml);
        // latest 入口内容即当天页
        assertThat(Files.readString(indexFile, StandardCharsets.UTF_8)).isEqualTo(dailyHtml);
        assertThat(Files.readString(archiveFile, StandardCharsets.UTF_8)).isEqualTo(archiveHtml);

        assertThat(url).isEqualTo("/news/" + date + ".html");
    }

    @Test
    void publish_createsDirWhenMissing() throws Exception {
        Path nested = tempDir.resolve("a").resolve("b").resolve("news");
        assertThat(Files.exists(nested)).isFalse();

        NewsHostingImpl impl = newHosting(nested.toString(), "/news");
        String date = "2026-01-01";
        impl.publish(date, "<html>d</html>", "<html>a</html>");

        assertThat(Files.isDirectory(nested)).isTrue();
        assertThat(nested.resolve(date + ".html")).exists();
    }

    @Test
    void publish_trimsTrailingSlashInPublicBase() {
        NewsHostingImpl impl = newHosting(tempDir.toString(), "/news/");
        String url = impl.publish("2026-02-02", "<html>d</html>", "<html>a</html>");
        assertThat(url).isEqualTo("/news/2026-02-02.html");
    }
}
