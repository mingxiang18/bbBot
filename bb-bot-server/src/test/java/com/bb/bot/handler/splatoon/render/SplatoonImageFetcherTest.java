package com.bb.bot.handler.splatoon.render;

import com.bb.bot.common.util.ResourcesUtils;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SplatoonImageFetcherTest {

    @Test
    void getImageFile_removesSignedUrlQueryFromCachePath() {
        ResourcesUtils resourcesUtils = mock(ResourcesUtils.class);
        File expected = new File("weapon.png");
        String url = "https://img.example.test/splatoon/weapon/18fdddee9c918842f076c10f12e46d891aca302d2677bf968ee2fe4e65b831a8_1.png"
                + "?Expires=1799539200&Signature=abc~def&Key-Pair-Id=KNBS2THMRC385";
        when(resourcesUtils.getOrAddStaticResourceFromNet(
                "splatoon/weapon/18fdddee9c918842f076c10f12e46d891aca302d2677bf968ee2fe4e65b831a8_1.png", url))
                .thenReturn(expected);

        SplatoonImageFetcher fetcher = new SplatoonImageFetcher();
        ReflectionTestUtils.setField(fetcher, "resourcesUtils", resourcesUtils);

        File actual = fetcher.getImageFile(url, "weapon");

        assertSame(expected, actual);
        verify(resourcesUtils).getOrAddStaticResourceFromNet(
                "splatoon/weapon/18fdddee9c918842f076c10f12e46d891aca302d2677bf968ee2fe4e65b831a8_1.png", url);
    }

    @Test
    void getImageFile_keepsFestivalTwoSegmentFileNameWithoutQuery() {
        ResourcesUtils resourcesUtils = mock(ResourcesUtils.class);
        File expected = new File("festival.png");
        String url = "https://img.example.test/festival/banners/fest_2026.png?Expires=1799539200";
        when(resourcesUtils.getOrAddStaticResourceFromNet(
                "splatoon/festival/bannersfest_2026.png", url))
                .thenReturn(expected);

        SplatoonImageFetcher fetcher = new SplatoonImageFetcher();
        ReflectionTestUtils.setField(fetcher, "resourcesUtils", resourcesUtils);

        File actual = fetcher.getImageFile(url, "festival");

        assertSame(expected, actual);
        verify(resourcesUtils).getOrAddStaticResourceFromNet("splatoon/festival/bannersfest_2026.png", url);
    }
}
