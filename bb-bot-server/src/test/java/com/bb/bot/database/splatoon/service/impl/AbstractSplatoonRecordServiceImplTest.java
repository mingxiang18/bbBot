package com.bb.bot.database.splatoon.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bb.bot.common.util.ResourcesUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * {@link AbstractSplatoonRecordServiceImpl} 单元测试。
 *
 * <p>覆盖 T5.4 上提到公共基类的同构资源保存骨架 {@code saveResourceFromImage}：
 * null 节点跳过、不同 imageKey（{@code image}/{@code originalImage}）、路径拼接
 * {@code nso_splatoon/{category}/{name}.png}、url 取自对应子对象。
 */
class AbstractSplatoonRecordServiceImplTest {

    /** 最小可实例化子类,仅用于驱动基类受保护方法。 */
    private static final class TestServiceImpl
            extends AbstractSplatoonRecordServiceImpl<BaseMapper<Object>, Object> {
    }

    private TestServiceImpl service;
    private ResourcesUtils resourcesUtils;

    @BeforeEach
    void setUp() {
        service = new TestServiceImpl();
        resourcesUtils = mock(ResourcesUtils.class);
        ReflectionTestUtils.setField(service, "resourcesUtils", resourcesUtils);
    }

    @Test
    void saveResourceFromImage_nullNode_noInteraction() {
        ReflectionTestUtils.invokeMethod(service, "saveResourceFromImage",
                "user/gear", "GearName", (JSONObject) null, "originalImage");
        verifyNoInteractions(resourcesUtils);
    }

    @Test
    void saveResourceFromImage_originalImageKey_composesPathAndUrl() {
        // 对战装备：图片 url 在 originalImage 键下
        JSONObject gear = new JSONObject()
                .fluentPut("originalImage", new JSONObject().fluentPut("url", "https://img/g.png"));

        ReflectionTestUtils.invokeMethod(service, "saveResourceFromImage",
                "user/gear", "GearName", gear, "originalImage");

        verify(resourcesUtils, times(1)).getOrAddStaticResourceFromNet(
                eq("nso_splatoon/user/gear/GearName.png"), eq("https://img/g.png"));
    }

    @Test
    void saveResourceFromImage_imageKey_composesPathAndUrl() {
        // 打工武器：图片 url 在 image 键下
        JSONObject weapon = new JSONObject()
                .fluentPut("image", new JSONObject().fluentPut("url", "https://img/w.png"));

        ReflectionTestUtils.invokeMethod(service, "saveResourceFromImage",
                "coop/weapon", "WeaponName", weapon, "image");

        verify(resourcesUtils, times(1)).getOrAddStaticResourceFromNet(
                eq("nso_splatoon/coop/weapon/WeaponName.png"), eq("https://img/w.png"));
        verify(resourcesUtils, never()).getOrAddStaticResourceFromNet(eq("other"), any());
    }
}
