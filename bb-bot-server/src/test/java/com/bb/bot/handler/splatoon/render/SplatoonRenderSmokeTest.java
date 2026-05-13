package com.bb.bot.handler.splatoon.render;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.common.util.ResourcesUtils;
import com.bb.bot.common.util.SpringUtils;
import com.bb.bot.common.util.fileClient.FileClientApi;
import com.bb.bot.common.util.fileClient.LocalFileClientApiImpl;
import com.bb.bot.config.FilePathConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.mockito.Mockito;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Splatoon 渲染器端到端冒烟测试。
 *
 * <p>不引入 Spring 容器：直接手动构造 {@link ResourcesUtils}（指向项目内的
 * {@code src/main/resources/static/}）+ {@link SplatoonImageFetcher}，然后驱动
 * {@link ScheduleMapRenderer} 从 {@code splatoon/schedule.json} fixture 渲染一张
 * 真实的对战图。
 *
 * <p>目的：
 * <ul>
 *   <li>验证从 BbSplatoonHandler 抽出的渲染代码仍能跑通；</li>
 *   <li>把 PNG 写到 {@code target/render-smoke/} 让人/机器可视化检查像素与重构前一致。</li>
 * </ul>
 *
 * <p>设置 {@code SKIP_RENDER_SMOKE=1} 环境变量可跳过本测试（CI 上若字体环境不齐时使用）。
 */
@DisabledIfEnvironmentVariable(named = "SKIP_RENDER_SMOKE", matches = "1")
class SplatoonRenderSmokeTest {

    private static final Path PROJECT_STATIC = Paths.get("src/main/resources/static/");
    private static final Path OUTPUT_DIR = Paths.get("target/render-smoke/");

    private static ScheduleMapRenderer scheduleMapRenderer;

    @BeforeAll
    static void setUp() throws IOException {
        Files.createDirectories(OUTPUT_DIR);
        Files.createDirectories(PROJECT_STATIC.resolve("tmp")); // FileUtils.buildTmpFile() 写入这里

        FilePathConfig pathConfig = new FilePathConfig();
        ReflectionTestUtils.setField(pathConfig, "filePath", PROJECT_STATIC.toAbsolutePath() + "/");

        // FileUtils.buildTmpFile() 通过 SpringUtils.getBean(FilePathConfig.class) 拿临时目录前缀，
        // 测试里没起 Spring 容器，这里塞一个只能返回 FilePathConfig 的桩 BeanFactory。
        ConfigurableListableBeanFactory beanFactory = Mockito.mock(ConfigurableListableBeanFactory.class);
        Mockito.when(beanFactory.getBean(FilePathConfig.class)).thenReturn(pathConfig);
        ReflectionTestUtils.setField(SpringUtils.class, "beanFactory", beanFactory);

        FileClientApi fileClient = new LocalFileClientApiImpl();
        ResourcesUtils resourcesUtils = new ResourcesUtils();
        ReflectionTestUtils.setField(resourcesUtils, "filePathConfig", pathConfig);
        ReflectionTestUtils.setField(resourcesUtils, "fileClientApi", fileClient);

        SplatoonImageFetcher imageFetcher = new SplatoonImageFetcher();
        ReflectionTestUtils.setField(imageFetcher, "resourcesUtils", resourcesUtils);

        scheduleMapRenderer = new ScheduleMapRenderer();
        ReflectionTestUtils.setField(scheduleMapRenderer, "resourcesUtils", resourcesUtils);
        ReflectionTestUtils.setField(scheduleMapRenderer, "imageFetcher", imageFetcher);
    }

    @Test
    void writeOneRegularMap_producesNonEmptyPng() throws IOException {
        JSONObject data = loadFixture();

        File rendered = scheduleMapRenderer.writeRegularMap(data, 0);

        assertRenderedSnapshot(rendered, "regular-map-index0.png");
    }

    @Test
    void writeOneCoopMap_producesNonEmptyPng() throws IOException {
        JSONObject data = loadFixture();

        File rendered = scheduleMapRenderer.writeCoopMap(data, 0);

        assertRenderedSnapshot(rendered, "coop-map-index0.png");
    }

    private JSONObject loadFixture() throws IOException {
        String json = Files.readString(PROJECT_STATIC.resolve("splatoon/schedule.json"));
        return JSON.parseObject(json).getJSONObject("data");
    }

    private void assertRenderedSnapshot(File rendered, String fileName) throws IOException {
        assertNotNull(rendered, "renderer must return a file");
        assertTrue(rendered.exists(), "rendered file must exist on disk: " + rendered);
        assertTrue(rendered.length() > 0, "rendered file must be non-empty");

        Path snapshot = OUTPUT_DIR.resolve(fileName);
        Files.copy(rendered.toPath(), snapshot, StandardCopyOption.REPLACE_EXISTING);
        assertTrue(Files.exists(snapshot));
        assertTrue(Files.size(snapshot) > 1000, "PNG should not be a trivial empty image");

        System.out.println("[render-smoke] " + fileName + " -> " + snapshot.toAbsolutePath());
    }
}
