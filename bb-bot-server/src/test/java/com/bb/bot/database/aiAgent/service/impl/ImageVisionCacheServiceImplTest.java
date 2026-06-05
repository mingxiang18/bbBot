package com.bb.bot.database.aiAgent.service.impl;

import com.bb.bot.database.aiAgent.entity.ImageVisionCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * {@link ImageVisionCacheServiceImpl} 单测：spy 出 ServiceImpl 的 getOne/save/updateById，
 * 验证命中计数自增、upsert、空值边界。不引 Spring 容器 / DB。
 */
class ImageVisionCacheServiceImplTest {

    private ImageVisionCacheServiceImpl service;

    @BeforeEach
    void setUp() {
        service = spy(new ImageVisionCacheServiceImpl());
    }

    @Test
    void findDescription_blank_returnsEmpty_noQuery() {
        assertThat(service.findDescription("  ")).isEmpty();
        assertThat(service.findDescription(null)).isEmpty();
        verify(service, never()).getOne(any());
    }

    @Test
    void findDescription_notFound_returnsEmpty() {
        doReturn(null).when(service).getOne(any());
        assertThat(service.findDescription("h")).isEmpty();
    }

    @Test
    void findDescription_found_returnsDesc_andBumpsHitCount() {
        ImageVisionCache row = new ImageVisionCache();
        row.setImageHash("h");
        row.setDescription("一只猫");
        row.setHitCount(2);
        doReturn(row).when(service).getOne(any());
        doReturn(true).when(service).updateById(any());

        Optional<String> r = service.findDescription("h");

        assertThat(r).contains("一只猫");
        assertThat(row.getHitCount()).isEqualTo(3);
        verify(service).updateById(row);
    }

    @Test
    void findDescription_foundButBlankDesc_returnsEmpty() {
        ImageVisionCache row = new ImageVisionCache();
        row.setDescription("   ");
        doReturn(row).when(service).getOne(any());
        assertThat(service.findDescription("h")).isEmpty();
    }

    @Test
    void put_blank_noOp() {
        service.put("", "d", "m");
        service.put("h", "  ", "m");
        verify(service, never()).save(any());
        verify(service, never()).getOne(any());
    }

    @Test
    void put_new_inserts_withHitCountZero() {
        doReturn(null).when(service).getOne(any());
        doReturn(true).when(service).save(any());

        service.put("h", "desc", "kimi-v");

        ArgumentCaptor<ImageVisionCache> cap = ArgumentCaptor.forClass(ImageVisionCache.class);
        verify(service).save(cap.capture());
        ImageVisionCache saved = cap.getValue();
        assertThat(saved.getImageHash()).isEqualTo("h");
        assertThat(saved.getDescription()).isEqualTo("desc");
        assertThat(saved.getModel()).isEqualTo("kimi-v");
        assertThat(saved.getHitCount()).isEqualTo(0);
    }

    @Test
    void put_existing_updatesInPlace_noInsert() {
        ImageVisionCache existing = new ImageVisionCache();
        existing.setImageHash("h");
        existing.setDescription("old");
        doReturn(existing).when(service).getOne(any());
        doReturn(true).when(service).updateById(any());

        service.put("h", "new", "kimi2");

        assertThat(existing.getDescription()).isEqualTo("new");
        assertThat(existing.getModel()).isEqualTo("kimi2");
        verify(service).updateById(existing);
        verify(service, never()).save(any());
    }
}
