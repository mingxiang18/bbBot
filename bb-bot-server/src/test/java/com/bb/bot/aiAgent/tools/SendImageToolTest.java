package com.bb.bot.aiAgent.tools;

import com.bb.bot.aiAgent.fs.AgentFileSpace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SendImageToolTest {

    @TempDir
    Path tmp;

    private SendImageTool tool;
    private RecordingSink sink;

    @BeforeEach
    void setUp() throws Exception {
        AgentFileSpace fileSpace = new AgentFileSpace();
        ReflectionTestUtils.setField(fileSpace, "userFileRoot", tmp.toString());
        Files.createDirectories(fileSpace.userRoot("u1"));

        tool = new SendImageTool();
        ReflectionTestUtils.setField(tool, "fileSpace", fileSpace);

        sink = new RecordingSink();
        AgentReplyContext.set(sink);
        MemoryToolContext.setUserId("u1");
    }

    @AfterEach
    void tearDown() {
        AgentReplyContext.clear();
        MemoryToolContext.clear();
    }

    @Test
    void send_validImage_usesImageSink() throws Exception {
        Path image = tmp.resolve("u1").resolve("out.png");
        Files.writeString(image, "fake image bytes");

        Map<String, Object> result = tool.send("out.png");

        assertEquals(true, result.get("ok"));
        assertEquals(image.toFile(), sink.sentImage);
    }

    @Test
    void send_rejectsNonImageFile() throws Exception {
        Path text = tmp.resolve("u1").resolve("out.txt");
        Files.writeString(text, "not an image");

        Map<String, Object> result = tool.send("out.txt");

        assertEquals("not_supported_image", result.get("error"));
    }

    @Test
    void send_rejectsEscapingPath() {
        Map<String, Object> result = tool.send("../u2/out.png");

        assertEquals("path_not_allowed", result.get("error"));
    }

    @Test
    void send_reportsNoImageCapability() throws Exception {
        sink.imageSupported = false;
        Path image = tmp.resolve("u1").resolve("out.png");
        Files.writeString(image, "fake image bytes");

        Map<String, Object> result = tool.send("out.png");

        assertEquals("client_no_image_capability", result.get("error"));
        assertTrue(sink.sentImage == null);
    }

    private static class RecordingSink implements AgentReplySink {
        private boolean imageSupported = true;
        private File sentImage;

        @Override
        public boolean fileSupported() {
            return true;
        }

        @Override
        public void sendFile(File file, String fileName) {
        }

        @Override
        public boolean imageSupported() {
            return imageSupported;
        }

        @Override
        public void sendImage(File image) {
            this.sentImage = image;
        }
    }
}
