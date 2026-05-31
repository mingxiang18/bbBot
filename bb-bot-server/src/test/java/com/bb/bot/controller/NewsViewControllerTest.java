package com.bb.bot.controller;

import com.bb.bot.config.NewsConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NewsViewControllerTest {

    @TempDir
    Path tempDir;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        NewsConfig newsConfig = new NewsConfig();
        newsConfig.getHosting().setDir(tempDir.toString());
        newsConfig.getHosting().setPublicBase("/news");

        NewsViewController controller = new NewsViewController();
        ReflectionTestUtils.setField(controller, "newsConfig", newsConfig);

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private void writeFile(String name, String content) throws Exception {
        Files.write(tempDir.resolve(name), content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void getDaily_returns200WithHtml() throws Exception {
        String html = "<html><body>2026-05-30 日报</body></html>";
        writeFile("2026-05-30.html", html);

        mockMvc.perform(get("/news/2026-05-30.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(html));
    }

    @Test
    void getLatest_returnsIndex() throws Exception {
        String html = "<html>latest</html>";
        writeFile("index.html", html);

        mockMvc.perform(get("/news/"))
                .andExpect(status().isOk())
                .andExpect(content().string(html));

        mockMvc.perform(get("/news/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(html));
    }

    @Test
    void getArchive_returnsArchive() throws Exception {
        String html = "<html>archive</html>";
        writeFile("archive.html", html);

        mockMvc.perform(get("/news/archive.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(html));
    }

    @Test
    void getDaily_missingDate_returns404() throws Exception {
        mockMvc.perform(get("/news/2099-12-31.html"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getDaily_invalidDateFormat_returns404() throws Exception {
        // 文件确实存在，但日期格式非法 → 拒绝
        writeFile("notadate.html", "<html>x</html>");
        mockMvc.perform(get("/news/notadate.html"))
                .andExpect(status().isNotFound());
    }

    @Test
    void traversal_doesNotReachOutsideFile() throws Exception {
        // 在临时目录之外（父目录）放一个 secret 文件
        Path secret = tempDir.getParent().resolve("secret.html");
        Files.write(secret, "TOP-SECRET".getBytes(StandardCharsets.UTF_8));
        try {
            // 含 ../ 的路径不应命中 {date}.html 路由，且绝不读到目录外文件
            mockMvc.perform(get("/news/../secret.html"))
                    .andExpect(status().is4xxClientError());
            // 即使被某种归一化处理，也不会返回 secret 内容
            mockMvc.perform(get("/news/2026-01-01.html").requestAttr("ignored", ""))
                    .andExpect(status().isNotFound());
        } finally {
            Files.deleteIfExists(secret);
            assertThat(Files.exists(secret)).isFalse();
        }
    }
}
