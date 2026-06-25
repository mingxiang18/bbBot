package com.bb.bot.handler.stardew;

import com.bb.bot.common.util.RestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StardewWikiApiClientTest {

    @Mock
    private RestUtils restUtils;

    @Test
    void searchesAndExtractsUsefulTextFromWikiApi() {
        StardewWikiApiClient client = new StardewWikiApiClient(restUtils);
        ReflectionTestUtils.setField(client, "apiUrl", "https://zh.stardewvalleywiki.com/mediawiki/api.php");
        ReflectionTestUtils.setField(client, "pageBaseUrl", "https://zh.stardewvalleywiki.com/");

        when(restUtils.get(contains("list=search"), any(HttpHeaders.class), eq(String.class)))
                .thenReturn("{\"query\":{\"search\":[]}}")
                .thenReturn("{\"query\":{\"search\":[{\"title\":\"战斗\"}]}}");
        when(restUtils.get(contains("action=parse"), any(HttpHeaders.class), eq(String.class)))
                .thenReturn("""
                        {"parse":{"displaytitle":"战斗","text":{"*":"<div class='toc'>目录</div><p>战斗是一种技能。</p><ul><li>击杀怪物会获得战斗经验。</li></ul><p>此页面最后编辑于某日。</p>"}}}
                        """);

        List<StardewWikiPage> pages = client.search("战斗技能如何快速升级", 1);

        assertThat(pages).hasSize(1);
        assertThat(pages.get(0).getTitle()).isEqualTo("战斗");
        assertThat(pages.get(0).getUrl()).contains("%E6%88%98%E6%96%97");
        assertThat(pages.get(0).getExcerpt()).contains("战斗是一种技能", "击杀怪物会获得战斗经验");
        assertThat(pages.get(0).getExcerpt()).doesNotContain("目录", "最后编辑");
    }

    @Test
    void ranksCoreCandidateAboveWeakOriginalQueryHit() {
        StardewWikiApiClient client = new StardewWikiApiClient(restUtils);
        ReflectionTestUtils.setField(client, "apiUrl", "https://zh.stardewvalleywiki.com/mediawiki/api.php");
        ReflectionTestUtils.setField(client, "pageBaseUrl", "https://zh.stardewvalleywiki.com/");

        when(restUtils.get(contains("list=search"), any(HttpHeaders.class), eq(String.class)))
                .thenReturn("{\"query\":{\"search\":[{\"title\":\"武器\"}]}}")
                .thenReturn("{\"query\":{\"search\":[{\"title\":\"银河之剑\"}]}}")
                .thenReturn("{\"query\":{\"search\":[]}}");
        when(restUtils.get(contains("action=parse"), any(HttpHeaders.class), eq(String.class)))
                .thenReturn("""
                        {"parse":{"displaytitle":"银河之剑","text":{"*":"<p>银河之剑是一种剑类武器。</p><p>玩家可以在沙漠使用五彩碎片取得。</p>"}}}
                        """);

        List<StardewWikiPage> pages = client.search("银河剑怎么拿", 1);

        assertThat(pages).hasSize(1);
        assertThat(pages.get(0).getTitle()).isEqualTo("银河之剑");
        assertThat(pages.get(0).getExcerpt()).contains("五彩碎片");
    }

    @Test
    void extractsUsefulRowsFromWikiTables() {
        StardewWikiApiClient client = new StardewWikiApiClient(restUtils);
        ReflectionTestUtils.setField(client, "apiUrl", "https://zh.stardewvalleywiki.com/mediawiki/api.php");
        ReflectionTestUtils.setField(client, "pageBaseUrl", "https://zh.stardewvalleywiki.com/");

        when(restUtils.get(contains("list=search"), any(HttpHeaders.class), eq(String.class)))
                .thenReturn("{\"query\":{\"search\":[{\"title\":\"洒水器\"}]}}");
        when(restUtils.get(contains("action=parse"), any(HttpHeaders.class), eq(String.class)))
                .thenReturn("""
                        {"parse":{"displaytitle":"洒水器","text":{"*":"<p>洒水器会每天早晨浇水。</p><table class='wikitable'><tr><th>物品</th><th>材料</th></tr><tr><td>优质洒水器</td><td>铁锭、金锭、精炼石英</td></tr></table>"}}}
                        """);

        List<StardewWikiPage> pages = client.search("优质洒水器怎么做", 1);

        assertThat(pages).hasSize(1);
        assertThat(pages.get(0).getExcerpt()).contains("洒水器会每天早晨浇水", "优质洒水器", "精炼石英");
    }
}
