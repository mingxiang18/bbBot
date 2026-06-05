package com.bb.bot.aiAgent.fs;

import com.bb.bot.common.util.HashUtil;
import com.bb.bot.common.util.RestUtils;
import com.bb.bot.constant.BbSendMessageType;
import com.bb.bot.entity.bb.BbMessageContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InboundImageStoreTest {

    @Mock
    AgentFileSpace fileSpace;
    @Mock
    RestUtils restUtils;

    InboundImageStore store;

    @TempDir
    Path tmp;

    @BeforeEach
    void setup() {
        store = new InboundImageStore();
        ReflectionTestUtils.setField(store, "fileSpace", fileSpace);
        ReflectionTestUtils.setField(store, "restUtils", restUtils);
        ReflectionTestUtils.setField(store, "imgBaseUrl", "");
    }

    @Test
    void extractHash_variants() {
        String h = "a".repeat(64);
        assertEquals(h, store.extractHash(h), "裸 hash");
        assertEquals(h, store.extractHash("/img/" + h + ".png"), "相对链接");
        assertEquals(h, store.extractHash("http://host/img/" + h + ".png"), "完整 URL");
        assertEquals(h, store.extractHash(h.toUpperCase()), "大写应规整为小写");
        assertNull(store.extractHash(null));
        assertNull(store.extractHash("   "));
        assertNull(store.extractHash("not-a-hash"));
        assertNull(store.extractHash("abc"), "太短(<16)");
        assertNull(store.extractHash("/img/zzz.png"), "非 hex");
    }

    @Test
    void normalize_localImage_writesByHash_rewritesToNetImageRef_andDedup() throws Exception {
        byte[] bytes = "PNGDATA-abc".getBytes(StandardCharsets.UTF_8);
        String hash = HashUtil.sha256Hex(bytes);
        Path cacheDir = tmp.resolve("_imgcache");
        when(fileSpace.userRoot("_imgcache")).thenReturn(cacheDir);

        String b64 = Base64.getEncoder().encodeToString(bytes);
        BbMessageContent img = BbMessageContent.builder()
                .type(BbSendMessageType.LOCAL_IMAGE).data("data:image/png;base64," + b64).build();
        List<BbMessageContent> contents = new ArrayList<>(List.of(
                BbMessageContent.buildTextContent("hi"), img));

        store.normalize(contents);

        // 落盘一份（按 hash 命名）
        Path saved = cacheDir.resolve(hash + ".png");
        assertTrue(Files.exists(saved), "应按 hash 落盘");
        assertArrayEquals(bytes, Files.readAllBytes(saved));
        // 该 part 改写成 netImage(ref)
        assertEquals(BbSendMessageType.NET_IMAGE, img.getType());
        assertEquals("/img/" + hash + ".png", img.getData());
        assertEquals(hash, img.getFileName());
        // 文本 part 不动
        assertEquals(BbSendMessageType.TEXT, contents.get(0).getType());

        // 去重：同图再来，文件名不变、bytesForRef 取得回原字节
        BbMessageContent img2 = BbMessageContent.builder()
                .type(BbSendMessageType.LOCAL_IMAGE).data("data:image/png;base64," + b64).build();
        List<BbMessageContent> contents2 = new ArrayList<>(List.of(img2));
        store.normalize(contents2);
        assertEquals("/img/" + hash + ".png", img2.getData());
        assertArrayEquals(bytes, store.bytesForRef("/img/" + hash + ".png"));
    }

    @Test
    void normalize_ignoresNonImage_nullAndEmpty() {
        List<BbMessageContent> contents = new ArrayList<>(List.of(
                BbMessageContent.buildTextContent("hi"),
                BbMessageContent.buildAtMessageContent("u1")));
        store.normalize(contents);  // 无图 → 不动、不抛
        assertEquals(BbSendMessageType.TEXT, contents.get(0).getType());
        assertEquals(BbSendMessageType.AT, contents.get(1).getType());
        store.normalize(null);
        store.normalize(new ArrayList<>());
    }

    @Test
    void bytesForRef_missingOrBadRef_returnsNull() {
        when(fileSpace.userRoot("_imgcache")).thenReturn(tmp.resolve("_imgcache"));
        assertNull(store.bytesForRef("/img/" + "b".repeat(64) + ".png"), "文件不存在");
        assertNull(store.bytesForRef("bad-ref"), "ref 非法");
    }
}
