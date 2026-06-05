package com.bb.bot.aiAgent.tools;

import com.bb.bot.aiAgent.fs.InboundImageStore;
import com.bb.bot.common.util.aiChat.provider.VisionDescriber;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyzeImageToolTest {

    @Mock
    InboundImageStore store;
    @Mock
    VisionDescriber describer;

    @InjectMocks
    AnalyzeImageTool tool;

    @Test
    void analyze_visionNotConfigured_returnsError() {
        when(describer.enabled()).thenReturn(false);
        Map<String, Object> r = tool.analyze("ref");
        assertEquals("vision_not_configured", r.get("error"));
    }

    @Test
    void analyze_imageNotFound_returnsError() {
        when(describer.enabled()).thenReturn(true);
        when(store.bytesForRef("ref")).thenReturn(null);
        Map<String, Object> r = tool.analyze("ref");
        assertEquals("image_not_found", r.get("error"));
    }

    @Test
    void analyze_success_returnsDescription() {
        byte[] b = "img-bytes".getBytes(StandardCharsets.UTF_8);
        when(describer.enabled()).thenReturn(true);
        when(store.bytesForRef("ref")).thenReturn(b);
        when(describer.describe(anyString(), eq(b))).thenReturn("一条狗在草地上");
        Map<String, Object> r = tool.analyze("ref");
        assertEquals("一条狗在草地上", r.get("description"));
        assertNull(r.get("error"));
    }

    @Test
    void analyze_visionReturnsNull_returnsError() {
        byte[] b = "img-bytes".getBytes(StandardCharsets.UTF_8);
        when(describer.enabled()).thenReturn(true);
        when(store.bytesForRef("ref")).thenReturn(b);
        when(describer.describe(anyString(), eq(b))).thenReturn(null);
        Map<String, Object> r = tool.analyze("ref");
        assertEquals("vision_failed", r.get("error"));
    }
}
