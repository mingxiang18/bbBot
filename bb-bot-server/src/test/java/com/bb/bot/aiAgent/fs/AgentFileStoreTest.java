package com.bb.bot.aiAgent.fs;

import com.bb.bot.constant.BbSendMessageType;
import com.bb.bot.entity.bb.BbMessageContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentFileStoreTest {

    @TempDir
    Path tmp;

    private AgentFileSpace space;
    private AgentFileStore store;

    @BeforeEach
    void setUp() {
        space = new AgentFileSpace();
        ReflectionTestUtils.setField(space, "userFileRoot", tmp.toString());
        store = new AgentFileStore();
        ReflectionTestUtils.setField(store, "fileSpace", space);
        // restUtils 留 null：localFile 走 base64 解码分支，不触网
    }

    private static BbMessageContent localFile(String fileName, String content) {
        String b64 = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
        return BbMessageContent.builder()
                .type(BbSendMessageType.LOCAL_FILE)
                .data(b64)
                .fileName(fileName)
                .build();
    }

    @Test
    void materializeInbound_localFile_decodesBase64AndRewritesDataToLocalPath() throws Exception {
        BbMessageContent file = localFile("note.txt", "hello world");
        List<BbMessageContent> contents = new ArrayList<>(List.of(
                BbMessageContent.buildTextContent("看看这个"), file));

        store.materializeInbound("u1", "msg-1", contents);

        Path saved = Paths.get(String.valueOf(file.getData()));
        assertTrue(saved.isAbsolute(), "data 应被改写成绝对路径");
        assertTrue(Files.exists(saved), "附件应已落盘");
        assertEquals("hello world", Files.readString(saved));
        assertTrue(saved.startsWith(space.userRoot("u1")), "应落在该用户目录子树内");
        // 文本附件不受影响
        assertEquals("看看这个", contents.get(0).getData());
    }

    @Test
    void materializeInbound_savesUnderInboundMessageIdSubdir() {
        BbMessageContent file = localFile("a.txt", "x");
        store.materializeInbound("u1", "msg-42", new ArrayList<>(List.of(file)));

        Path saved = Paths.get(String.valueOf(file.getData()));
        Path expectedDir = space.userRoot("u1").resolve("inbound").resolve("msg-42");
        assertEquals(expectedDir, saved.getParent());
    }

    @Test
    void materializeInbound_differentUsers_landInIsolatedDirs() {
        BbMessageContent fa = localFile("a.txt", "alice-data");
        BbMessageContent fb = localFile("a.txt", "bob-data");
        store.materializeInbound("alice", "m", new ArrayList<>(List.of(fa)));
        store.materializeInbound("bob", "m", new ArrayList<>(List.of(fb)));

        Path savedA = Paths.get(String.valueOf(fa.getData()));
        Path savedB = Paths.get(String.valueOf(fb.getData()));
        assertTrue(savedA.startsWith(space.userRoot("alice")));
        assertTrue(savedB.startsWith(space.userRoot("bob")));
        assertNotEquals(savedA, savedB);
    }

    @Test
    void materializeInbound_sanitizesTraversalInFileName() {
        BbMessageContent file = localFile("../../evil.sh", "payload");
        store.materializeInbound("u1", "m", new ArrayList<>(List.of(file)));

        Path saved = Paths.get(String.valueOf(file.getData()));
        // 文件名里的 ../ 被清洗，最终仍落在该用户目录内
        assertTrue(saved.startsWith(space.userRoot("u1")));
        assertTrue(saved.getFileName().toString().endsWith("evil.sh"));
    }

    @Test
    void materializeInbound_sameNameTwice_doesNotOverwrite() {
        BbMessageContent f1 = localFile("dup.txt", "first");
        BbMessageContent f2 = localFile("dup.txt", "second");
        store.materializeInbound("u1", "m", new ArrayList<>(List.of(f1, f2)));

        Path s1 = Paths.get(String.valueOf(f1.getData()));
        Path s2 = Paths.get(String.valueOf(f2.getData()));
        assertNotEquals(s1, s2, "同名附件不应互相覆盖");
    }

    @Test
    void materializeInbound_emptyOrNoAttachments_noop() {
        List<BbMessageContent> textOnly = new ArrayList<>(List.of(
                BbMessageContent.buildTextContent("纯文本")));
        store.materializeInbound("u1", "m", textOnly);
        assertEquals("纯文本", textOnly.get(0).getData());
        // null / 空列表不抛异常
        store.materializeInbound("u1", "m", null);
        store.materializeInbound("u1", "m", new ArrayList<>());
    }
}
