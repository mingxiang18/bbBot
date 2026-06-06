package com.bb.bot.handler.splatoon;

import com.bb.bot.common.util.ResourcesUtils;
import com.bb.bot.common.util.RestUtils;
import com.bb.bot.common.util.SpringUtils;
import com.bb.bot.common.util.fileClient.FileClientApi;
import com.bb.bot.common.util.fileClient.LocalFileClientApiImpl;
import com.bb.bot.config.FilePathConfig;
import com.bb.bot.handler.splatoon.render.ScheduleMapRenderer;
import com.bb.bot.handler.splatoon.render.SplatoonImageFetcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Mockito;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SplatoonScheduleCache} 活体链路测试：真连 splatoon3.ink + 真渲染 + 真落盘。
 *
 * <p>默认<strong>不</strong>跑（需网络、耗时、会下载 CDN 图）；设环境变量
 * {@code RUN_SPLATOON_LIVE=1} 才启用。零 Spring 容器，仿
 * {@code SplatoonRenderSmokeTest}：把渲染需要的只读静态资源（字体 / 背景 / mode 图标 /
 * coop boss 图）拷进一个临时目录当 {@code file.path}，stages/weapons 由 refresh 现下载进
 * 临时区，<strong>不污染源码树</strong>；测完整目录删除。</p>
 *
 * <p>断言：refresh() 后对战/打工时段非空且落盘文件真实存在；getRegularMap/getCoopMap(0)
 * 命中缓存返回存在的文件，且二次调用返回同一落盘路径（无异常、确实走缓存而非每次重渲）。</p>
 */
@EnabledIfEnvironmentVariable(named = "RUN_SPLATOON_LIVE", matches = "1")
class SplatoonScheduleCacheLiveTest {

    private static final Path PROJECT_STATIC = Paths.get("src/main/resources/static/");
    /** 渲染直接 getStaticResource 读取、必须预置的只读子树。 */
    private static final String[] READONLY_SUBTREES = {
            "font", "splatoon/background", "splatoon/mode", "nso_splatoon/coop/boss"
    };

    private Path tempRoot;
    private SplatoonScheduleCache cache;

    @BeforeEach
    void setUp() throws IOException {
        tempRoot = Files.createTempDirectory("bb-splatoon-live-");
        // 预置只读资源 + 临时输出目录
        for (String sub : READONLY_SUBTREES) {
            copyTree(PROJECT_STATIC.resolve(sub), tempRoot.resolve(sub));
        }
        Files.createDirectories(tempRoot.resolve("tmp"));
        Files.createDirectories(tempRoot.resolve("splatoon/schedule"));

        FilePathConfig pathConfig = new FilePathConfig();
        ReflectionTestUtils.setField(pathConfig, "filePath", tempRoot.toAbsolutePath() + "/");

        // FileUtils.getAbsolutePath/buildTmpFile 通过 SpringUtils 拿 FilePathConfig
        ConfigurableListableBeanFactory beanFactory = Mockito.mock(ConfigurableListableBeanFactory.class);
        Mockito.when(beanFactory.getBean(FilePathConfig.class)).thenReturn(pathConfig);
        ReflectionTestUtils.setField(SpringUtils.class, "beanFactory", beanFactory);

        RestUtils restUtils = new RestUtils();
        ReflectionTestUtils.setField(restUtils, "restClient", RestClient.builder().build()); // 无代理直连

        FileClientApi fileClient = new LocalFileClientApiImpl();
        ResourcesUtils resourcesUtils = new ResourcesUtils();
        ReflectionTestUtils.setField(resourcesUtils, "filePathConfig", pathConfig);
        ReflectionTestUtils.setField(resourcesUtils, "fileClientApi", fileClient);
        ReflectionTestUtils.setField(resourcesUtils, "restUtils", restUtils); // 下载 stage/weapon CDN 图要用

        SplatoonImageFetcher imageFetcher = new SplatoonImageFetcher();
        ReflectionTestUtils.setField(imageFetcher, "resourcesUtils", resourcesUtils);

        ScheduleMapRenderer renderer = new ScheduleMapRenderer();
        ReflectionTestUtils.setField(renderer, "resourcesUtils", resourcesUtils);
        ReflectionTestUtils.setField(renderer, "imageFetcher", imageFetcher);

        cache = new SplatoonScheduleCache();
        ReflectionTestUtils.setField(cache, "restUtils", restUtils);
        ReflectionTestUtils.setField(cache, "scheduleMapRenderer", renderer);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempRoot != null && Files.exists(tempRoot)) {
            try (Stream<Path> walk = Files.walk(tempRoot)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    @Test
    void refresh_thenReadsHitCache_realLiveChain() throws Exception {
        cache.refresh();

        List<?> regularSlots = (List<?>) ReflectionTestUtils.getField(cache, "regularSlots");
        List<?> coopSlots = (List<?>) ReflectionTestUtils.getField(cache, "coopSlots");
        assertNotNull(regularSlots);
        assertNotNull(coopSlots);
        assertFalse(regularSlots.isEmpty(), "对战时段应预渲染出多段");
        assertFalse(coopSlots.isEmpty(), "打工时段应预渲染出多段");
        System.out.println("[live] 对战 " + regularSlots.size() + " 段，打工 " + coopSlots.size() + " 段");

        // 落盘目录里确有图
        File scheduleDir = tempRoot.resolve("splatoon/schedule").toFile();
        File[] onDisk = scheduleDir.listFiles((d, n) -> n.endsWith(".png"));
        assertNotNull(onDisk);
        assertTrue(onDisk.length > 0, "落盘目录应有预渲染图");
        System.out.println("[live] 落盘图数量：" + onDisk.length);

        // 当前时段命中缓存：返回存在的文件
        File regularNow = cache.getRegularMap(0);
        assertNotNull(regularNow);
        assertTrue(regularNow.exists() && regularNow.length() > 1000, "对战当前图应存在且非空：" + regularNow);

        File coopNow = cache.getCoopMap(0);
        assertNotNull(coopNow);
        assertTrue(coopNow.exists() && coopNow.length() > 1000, "打工当前图应存在且非空：" + coopNow);
        // 「工」应是「当前段 + 下一段」两张拼接：单张约 337px 高，两张约 674px。
        // 当前打工段几乎总有下一段，故断言明显高于单张（>500），守住两张面板不退化成一张。
        int coopHeight = javax.imageio.ImageIO.read(coopNow).getHeight();
        assertTrue(coopHeight > 500, "打工当前图应为当前+下一段两张拼接（高约674），实际高=" + coopHeight);
        System.out.println("[live] 打工当前图高度：" + coopHeight + "（两张拼接）");

        // 二次调用走缓存：返回同一落盘路径（在 splatoon/schedule 下，而非 tmp 下的实时渲染产物）
        File regularNow2 = cache.getRegularMap(0);
        assertTrue(regularNow2.getPath().equals(regularNow.getPath()), "二次读取应命中同一缓存文件");
        assertTrue(regularNow.getParentFile().getName().equals("schedule"), "命中文件应在 schedule 缓存目录");
        System.out.println("[live] 对战当前图：" + regularNow.getPath());
        System.out.println("[live] 打工当前图：" + coopNow.getPath());
    }

    /** 递归复制一个子树（源不存在则跳过）。 */
    private static void copyTree(Path src, Path dst) throws IOException {
        if (!Files.exists(src)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(src)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                Path target = dst.resolve(src.relativize(p));
                if (Files.isDirectory(p)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(p, target);
                }
            }
        }
    }
}
