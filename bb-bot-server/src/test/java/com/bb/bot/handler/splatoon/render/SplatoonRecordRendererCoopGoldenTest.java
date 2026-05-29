package com.bb.bot.handler.splatoon.render;

import com.bb.bot.common.util.FileUtils;
import com.bb.bot.common.util.ImageUtils;
import com.bb.bot.common.util.ResourcesUtils;
import com.bb.bot.common.util.SpringUtils;
import com.bb.bot.common.util.fileClient.FileClientApi;
import com.bb.bot.common.util.fileClient.LocalFileClientApiImpl;
import com.bb.bot.config.FilePathConfig;
import com.bb.bot.database.splatoon.entity.SplatoonCoopRecord;
import com.bb.bot.database.splatoon.entity.SplatoonCoopUserDetail;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.mockito.Mockito;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code SplatoonRecordRenderer.writeOneCoopRecord} 的 golden-image 测试（T7.7）。
 *
 * <p>该方法把一条打工记录画到调用方传入的 {@link Graphics2D} 上，没有随机性、没有时间相关
 * 绘制（{@code playedTime} 固定 fixture），给定相同 record / userDetail 输出是确定的位图。
 * 本任务把方法体拆成 {@code drawCoopHeader}/{@code drawCoopWeapons}/{@code drawCoopPlayerBlock}，
 * 属纯提取重构，绘制顺序与参数逐字保留，因此重构前后像素应当一致。</p>
 *
 * <p>测试驱动方式照搬 {@link SplatoonRenderSmokeTest}：手动构造指向 {@code src/main/resources/static/}
 * 的 {@link ResourcesUtils}，把真实字体/图标喂给渲染器。先把当前（重构前）输出固化为
 * {@code src/test/resources/golden/coop-record-*.png} 参考图，重构后用同样输入渲染并按 D2 容差
 * （逐像素 RGB 差<=2 或差异像素占比<0.5%）比对。</p>
 *
 * <p>覆盖两条分支：
 * <ul>
 *   <li>{@code afterGradeId} 非空 → 画段位/点数/升降；满 4 人详情块；</li>
 *   <li>{@code afterGradeId} 空 → 画 {@code ruleMap} 模式名；3 人详情块（含 null 计数字段兜底）。</li>
 * </ul>
 * 字体环境不齐时设 {@code SKIP_RENDER_SMOKE=1} 跳过。</p>
 */
@DisabledIfEnvironmentVariable(named = "SKIP_RENDER_SMOKE", matches = "1")
class SplatoonRecordRendererCoopGoldenTest {

    private static final Path PROJECT_STATIC = Paths.get("src/main/resources/static/");
    private static final Path GOLDEN_DIR = Paths.get("src/test/resources/golden");
    private static final Path OUTPUT_DIR = Paths.get("target/render-golden/");

    private static SplatoonRecordRenderer renderer;

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

        renderer = new SplatoonRecordRenderer();
        ReflectionTestUtils.setField(renderer, "resourcesUtils", resourcesUtils);
    }

    /** afterGradeId 非空分支 + 满 4 人详情块。 */
    @Test
    void writeOneCoopRecord_gradeBranch_matchesGolden() throws IOException {
        BufferedImage actual = renderOneCoop(gradeRecord(), fourPlayers());
        assertMatchesGolden("coop-record-grade.png", actual);
    }

    /** afterGradeId 空分支（走 ruleMap 模式名）+ 3 人详情块（含 null 字段）。 */
    @Test
    void writeOneCoopRecord_ruleBranch_matchesGolden() throws IOException {
        BufferedImage actual = renderOneCoop(ruleRecord(), threePlayers());
        assertMatchesGolden("coop-record-rule.png", actual);
    }

    private BufferedImage renderOneCoop(SplatoonCoopRecord record, List<SplatoonCoopUserDetail> details) throws IOException {
        File backgroundImage = ((ResourcesUtils) ReflectionTestUtils.getField(renderer, "resourcesUtils"))
                .getStaticResource("splatoon/background/bg_good.jpg");
        File imageFile = FileUtils.buildTmpFile();
        ImageUtils.cropImage(backgroundImage, imageFile, 0, 0, 720, 720);
        BufferedImage image = ImageIO.read(imageFile);
        Graphics2D g2d = ImageUtils.createDefaultG2dFromFile(image);
        renderer.writeOneCoopRecord(g2d, record, details, 20);
        g2d.dispose();
        return image;
    }

    private SplatoonCoopRecord gradeRecord() {
        SplatoonCoopRecord r = new SplatoonCoopRecord();
        r.setId(1024L);
        r.setPlayedTime(LocalDateTime.of(2026, 1, 2, 3, 4, 5));
        r.setCoopStageName("海女美术大学");
        r.setWeapon1("N-ZAP85");
        r.setWeapon2("H3捲管槍");
        r.setWeapon3("L3捲管槍");
        r.setWeapon4("LACT-450");
        r.setDangerRate("333.3");
        r.setTeamGlodenCount(42);
        r.setTeamRedCount(99);
        r.setAfterGradeId("grade-7");
        r.setAfterGradeName("名人");
        r.setAfterGradePoint(120);
        r.setGradePointDiff("UP");
        return r;
    }

    private SplatoonCoopRecord ruleRecord() {
        SplatoonCoopRecord r = new SplatoonCoopRecord();
        r.setId(7L);
        r.setPlayedTime(LocalDateTime.of(2026, 5, 30, 18, 0, 0));
        r.setCoopStageName("烧窑发电所");
        r.setWeapon1("N-ZAP85");
        r.setWeapon2("N-ZAP85");
        r.setWeapon3("N-ZAP85");
        r.setWeapon4("N-ZAP85");
        r.setDangerRate("100.0");
        r.setTeamGlodenCount(0);
        r.setTeamRedCount(0);
        // afterGradeId 留空 → 走 ruleMap 分支
        r.setRule("BIG_RUN");
        return r;
    }

    private List<SplatoonCoopUserDetail> fourPlayers() {
        List<SplatoonCoopUserDetail> list = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            SplatoonCoopUserDetail d = new SplatoonCoopUserDetail();
            d.setCoopId("1024");
            d.setPlayerName("玩家" + i);
            d.setDefeatEnemyCount(10 + i);
            d.setDeliverGlodenCount(5 + i);
            d.setDeliverRedCount(20 + i);
            d.setRescueCount(i);
            d.setRescuedCount(i + 1);
            list.add(d);
        }
        return list;
    }

    private List<SplatoonCoopUserDetail> threePlayers() {
        List<SplatoonCoopUserDetail> list = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            SplatoonCoopUserDetail d = new SplatoonCoopUserDetail();
            d.setCoopId("7");
            d.setPlayerName("P" + i);
            d.setDefeatEnemyCount(0);
            d.setDeliverGlodenCount(0);
            d.setDeliverRedCount(0);
            d.setRescueCount(0);
            d.setRescuedCount(0);
            list.add(d);
        }
        return list;
    }

    private void assertMatchesGolden(String fileName, BufferedImage actual) throws IOException {
        Path snapshot = OUTPUT_DIR.resolve(fileName);
        ImageIO.write(actual, "png", snapshot.toFile());

        String resourcePath = "golden/" + fileName;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                Files.createDirectories(GOLDEN_DIR);
                ImageIO.write(actual, "png", GOLDEN_DIR.resolve(fileName).toFile());
                throw new IllegalStateException("golden 不存在，已生成 " + GOLDEN_DIR.resolve(fileName)
                        + "，请确认后重跑（提交前应已固化）。");
            }
            BufferedImage expected = ImageIO.read(in);
            assertPixelEquivalent(expected, actual);
        }
    }

    /** D2 容差：逐像素 RGB 差<=2 视为相同，差异像素占比 < 0.5% 视为等价。 */
    private void assertPixelEquivalent(BufferedImage expected, BufferedImage actual) {
        assertEquals(expected.getWidth(), actual.getWidth(), "width mismatch");
        assertEquals(expected.getHeight(), actual.getHeight(), "height mismatch");
        int w = expected.getWidth();
        int h = expected.getHeight();
        long diffPixels = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int e = expected.getRGB(x, y);
                int a = actual.getRGB(x, y);
                if (e == a) {
                    continue;
                }
                int dr = Math.abs(((e >> 16) & 0xFF) - ((a >> 16) & 0xFF));
                int dg = Math.abs(((e >> 8) & 0xFF) - ((a >> 8) & 0xFF));
                int db = Math.abs((e & 0xFF) - (a & 0xFF));
                int da = Math.abs(((e >> 24) & 0xFF) - ((a >> 24) & 0xFF));
                if (dr > 2 || dg > 2 || db > 2 || da > 2) {
                    diffPixels++;
                }
            }
        }
        double ratio = (double) diffPixels / (w * h);
        assertThat(ratio)
                .as("差异像素占比应 < 0.5%%（diff=%d, total=%d）", diffPixels, (long) w * h)
                .isLessThan(0.005);
    }
}
