package com.bb.bot.handler.splatoon.render;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 使用原型阶段从 SplatNet 拉到的真实 JSON fixture，实际生成扩展页面 PNG。
 *
 * <p>输出到 {@code target/splatoon-expanded-pages/}，用于人工打开核对暗色主题、
 * openhtmltopdf 兼容性以及字段路径是否真实可用。</p>
 */
@DisabledIfEnvironmentVariable(named = "SKIP_RENDER_SMOKE", matches = "1")
class SplatoonExpandedPagesRenderSmokeTest {

    private static final Path PROJECT_STATIC = Paths.get("src/main/resources/static/");
    private static final Path OUTPUT_DIR = Paths.get("target/splatoon-expanded-pages/");
    private static final Path FIXTURE_DIR = Paths.get("/private/tmp/splatoon-page-prototypes/data/");

    private static SplatoonHtmlRenderer renderer;

    @BeforeAll
    static void setUp() throws IOException {
        Files.createDirectories(OUTPUT_DIR);
        Files.createDirectories(PROJECT_STATIC.resolve("tmp"));

        FilePathConfig pathConfig = new FilePathConfig();
        ReflectionTestUtils.setField(pathConfig, "filePath", PROJECT_STATIC.toAbsolutePath() + "/");

        ConfigurableListableBeanFactory beanFactory = Mockito.mock(ConfigurableListableBeanFactory.class);
        Mockito.when(beanFactory.getBean(FilePathConfig.class)).thenReturn(pathConfig);
        ReflectionTestUtils.setField(SpringUtils.class, "beanFactory", beanFactory);

        FileClientApi fileClient = new LocalFileClientApiImpl();
        ResourcesUtils resourcesUtils = new ResourcesUtils();
        ReflectionTestUtils.setField(resourcesUtils, "filePathConfig", pathConfig);
        ReflectionTestUtils.setField(resourcesUtils, "fileClientApi", fileClient);

        SplatoonImageFetcher imageFetcher = new SplatoonImageFetcher();
        ReflectionTestUtils.setField(imageFetcher, "resourcesUtils", resourcesUtils);

        renderer = new SplatoonHtmlRenderer();
        ReflectionTestUtils.setField(renderer, "resourcesUtils", resourcesUtils);
        ReflectionTestUtils.setField(renderer, "imageFetcher", imageFetcher);
    }

    @Test
    void renderExpandedPagesFromRealFixtures_producesNonEmptyPngs() throws IOException {
        assertRendered(renderer.renderOverview(
                fixture("HomeQuery.json"),
                fixture("HistorySummary.json"),
                fixture("TotalQuery.json")), "01-overview.png");

        assertRendered(renderer.renderCoopStatistics(fixture("CoopStatistics.json")), "02-coop-statistics.png");

        JSONObject xRanking = fixture("XRankingQuery_ATLANTIC.json");
        assertRendered(renderer.renderXRankingHub(xRanking), "03-x-ranking-hub.png");
        assertRendered(renderer.renderXRankingMode("区域", "ATLANTIC", xTopHolders("Ar")), "04-x-ranking-ar.png");

        assertRendered(renderer.renderEventBoard(fixture("EventBoardQuery.json").getJSONObject("data").getJSONObject("rankingPeriod")),
                "05-event-board.png");

        assertRendered(renderer.renderStageGear(
                fixture("StageRecordsQuery.json"),
                fixture("MyOutfitCommonDataEquipmentsQuery.json")), "06-stage-gear.png");

        assertRendered(renderer.renderFriends(fixture("FriendsList.json")), "07-friends.png");
    }

    private JSONArray xTopHolders(String modeCode) throws IOException {
        JSONObject fixture = fixture("XRankingRefetchTop10_ATLANTIC.json");
        JSONArray edges = fixture.getJSONObject("modes")
                .getJSONObject(modeCode)
                .getJSONObject("data")
                .getJSONObject("node")
                .getJSONObject("xRanking" + modeCode)
                .getJSONArray("edges");
        JSONArray holders = new JSONArray();
        for (int i = 0; i < edges.size(); i++) {
            holders.add(edges.getJSONObject(i).getJSONObject("node"));
        }
        return holders;
    }

    private JSONObject fixture(String name) throws IOException {
        return JSON.parseObject(Files.readString(FIXTURE_DIR.resolve(name)));
    }

    private void assertRendered(File rendered, String fileName) throws IOException {
        assertNotNull(rendered, "renderer must return a file");
        assertTrue(rendered.exists(), "rendered file must exist on disk: " + rendered);
        assertTrue(rendered.length() > 1000, "rendered file should be a non-trivial PNG");

        Path snapshot = OUTPUT_DIR.resolve(fileName);
        Files.copy(rendered.toPath(), snapshot, StandardCopyOption.REPLACE_EXISTING);

        BufferedImage image = ImageIO.read(snapshot.toFile());
        assertNotNull(image, "snapshot should be readable as an image");
        assertTrue(image.getWidth() > 200, "snapshot width should be meaningful");
        assertTrue(image.getHeight() > 200, "snapshot height should be meaningful");
        System.out.println("[splatoon-expanded-pages] " + fileName + " -> " + snapshot.toAbsolutePath()
                + " (" + image.getWidth() + "x" + image.getHeight() + ")");
    }
}
