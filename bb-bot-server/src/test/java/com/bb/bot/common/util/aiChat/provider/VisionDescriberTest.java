package com.bb.bot.common.util.aiChat.provider;

import com.bb.bot.database.aiAgent.service.IImageVisionCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VisionDescriberTest {

    @Mock
    AiChatService aiChatService;
    @Mock
    IImageVisionCacheService cacheService;

    VisionDescriber describer;

    @BeforeEach
    void setup() {
        describer = new VisionDescriber();
        ReflectionTestUtils.setField(describer, "aiChatService", aiChatService);
        ReflectionTestUtils.setField(describer, "cacheService", cacheService);
    }

    private static byte[] pngBytes() {
        return new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1, 2, 3};
    }

    @Test
    void describe_nullOrEmptyInputs_returnsNull() {
        assertNull(describer.describe(null, new byte[]{1}));
        assertNull(describer.describe("h", null));
        assertNull(describer.describe("h", new byte[0]));
        verify(aiChatService, never()).chat(anyList(), eq(ModelTier.VISION));
    }

    @Test
    void describe_dbHit_returnsCached_withoutCallingModel() {
        when(cacheService.findDescription("h")).thenReturn(Optional.of("cached desc"));
        assertEquals("cached desc", describer.describe("h", new byte[]{1, 2, 3}));
        verify(aiChatService, never()).chat(anyList(), eq(ModelTier.VISION));
    }

    @Test
    void describe_visionNotConfigured_returnsNull() {
        when(cacheService.findDescription("h")).thenReturn(Optional.empty());
        when(aiChatService.visionConfigured()).thenReturn(false);
        assertNull(describer.describe("h", pngBytes()));
        verify(aiChatService, never()).chat(anyList(), eq(ModelTier.VISION));
    }

    @Test
    void describe_miss_callsModel_writesBothCaches_andMemoizes() {
        when(cacheService.findDescription("h")).thenReturn(Optional.empty());
        when(aiChatService.visionConfigured()).thenReturn(true);
        ModelSpec spec = mock(ModelSpec.class);
        when(spec.getModel()).thenReturn("kimi-v");
        when(aiChatService.specForTier(ModelTier.VISION)).thenReturn(spec);
        when(aiChatService.chat(anyList(), eq(ModelTier.VISION))).thenReturn("  一只猫  ");

        assertEquals("一只猫", describer.describe("h", pngBytes()), "应 trim");
        verify(cacheService).put("h", "一只猫", "kimi-v");

        // 第二次同 hash → 命中内存，不再调模型
        assertEquals("一只猫", describer.describe("h", pngBytes()));
        verify(aiChatService, times(1)).chat(anyList(), eq(ModelTier.VISION));
    }

    @Test
    void describe_modelReturnsBlank_returnsNull_noPut() {
        when(cacheService.findDescription("h")).thenReturn(Optional.empty());
        when(aiChatService.visionConfigured()).thenReturn(true);
        when(aiChatService.specForTier(ModelTier.VISION)).thenReturn(mock(ModelSpec.class));
        when(aiChatService.chat(anyList(), eq(ModelTier.VISION))).thenReturn("   ");
        assertNull(describer.describe("h", pngBytes()));
        verify(cacheService, never()).put(eq("h"), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void describe_modelThrows_returnsNull() {
        when(cacheService.findDescription("h")).thenReturn(Optional.empty());
        when(aiChatService.visionConfigured()).thenReturn(true);
        when(aiChatService.specForTier(ModelTier.VISION)).thenReturn(mock(ModelSpec.class));
        when(aiChatService.chat(anyList(), eq(ModelTier.VISION)))
                .thenThrow(new RuntimeException("api down"));
        assertNull(describer.describe("h", pngBytes()));
    }

    @Test
    void enabled_reflectsVisionConfigured() {
        when(aiChatService.visionConfigured()).thenReturn(true);
        org.junit.jupiter.api.Assertions.assertTrue(describer.enabled());
    }
}
