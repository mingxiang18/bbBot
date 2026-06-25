package com.bb.bot.aiAgent.tools;

import com.bb.bot.handler.stardew.StardewGuideService;
import com.bb.bot.handler.stardew.StardewWikiPage;
import com.bb.bot.handler.stardew.StardewKnowledgeRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StardewGuideToolTest {

    @Test
    void returnsStructuredToolResult() {
        StardewKnowledgeRepository repository = new StardewKnowledgeRepository();
        repository.load();
        StardewGuideTool tool = new StardewGuideTool(new StardewGuideService(repository));

        Map<String, Object> result = tool.guide("电池组怎么获得");

        assertThat(result).containsEntry("intent", "resource");
        assertThat(result.get("evidence").toString()).contains("避雷针", "太阳能板");
        assertThat(result.get("replyInstruction").toString()).contains("自然", "不要声称读取了用户存档");
        assertThat(result).doesNotContainKeys("sourceUrls", "gameVersion", "lastCheckedAt", "note");
    }

    @Test
    void toolCoversUpgradeQuestions() {
        StardewKnowledgeRepository repository = new StardewKnowledgeRepository();
        repository.load();
        StardewGuideTool tool = new StardewGuideTool(new StardewGuideService(repository));

        Map<String, Object> result = tool.guide("喷壶升级需要多少钱和什么材料");

        assertThat(result).containsEntry("intent", "tool_upgrade_detail");
        assertThat(result.get("evidence").toString()).contains("铜喷壶", "2,000g", "钢喷壶", "5,000g");
    }

    @Test
    void toolCoversSpecificToolUpgradeTier() {
        StardewKnowledgeRepository repository = new StardewKnowledgeRepository();
        repository.load();
        StardewGuideTool tool = new StardewGuideTool(new StardewGuideService(repository));

        Map<String, Object> result = tool.guide("铱镐需要多少钱");

        assertThat(result).containsEntry("intent", "tool_upgrade_detail");
        assertThat(result.get("evidence").toString()).contains("铱镐需要：25,000g + 铱锭 x5", "前置：金镐");
    }

    @Test
    void toolCoversVillagerGiftQuestions() {
        StardewKnowledgeRepository repository = new StardewKnowledgeRepository();
        repository.load();
        StardewGuideTool tool = new StardewGuideTool(new StardewGuideService(repository));

        Map<String, Object> result = tool.guide("莉亚生日和喜欢什么");

        assertThat(result).containsEntry("intent", "villager_profile");
        assertThat(result.get("evidence").toString()).contains("冬季 23 日", "沙拉", "山羊奶酪");
    }

    @Test
    void toolCoversExpandedVillagerProfiles() {
        StardewKnowledgeRepository repository = new StardewKnowledgeRepository();
        repository.load();
        StardewGuideTool tool = new StardewGuideTool(new StardewGuideService(repository));

        Map<String, Object> result = tool.guide("海莉生日和最爱礼物");

        assertThat(result).containsEntry("intent", "villager_profile");
        assertThat(result.get("evidence").toString()).contains("春季 14 日", "椰子", "粉红蛋糕", "向日葵");
    }

    @Test
    void toolCoversCommonShopQuestions() {
        StardewKnowledgeRepository repository = new StardewKnowledgeRepository();
        repository.load();
        StardewGuideTool tool = new StardewGuideTool(new StardewGuideService(repository));

        Map<String, Object> result = tool.guide("背包升级多少钱");

        assertThat(result).containsEntry("intent", "shop_item");
        assertThat(result.get("evidence").toString()).contains("皮埃尔", "大背包", "2,000g", "豪华背包", "10,000g");
    }

    @Test
    void toolCanReturnWikiFallback() {
        StardewKnowledgeRepository repository = new StardewKnowledgeRepository();
        repository.load();
        StardewGuideTool tool = new StardewGuideTool(new StardewGuideService(repository, (query, maxResults) -> List.of(
                StardewWikiPage.builder()
                        .title("金核桃")
                        .url("https://zh.stardewvalleywiki.com/金核桃")
                        .excerpt("金核桃是姜岛上的特殊货币，可通过探索、解谜、钓鱼、战斗和耕种等方式获得。")
                        .build())));

        Map<String, Object> result = tool.guide("姜岛金核桃怎么收集");

        assertThat(result).containsEntry("intent", "wiki_fallback");
        assertThat(result.get("evidence").toString()).contains("金核桃", "姜岛");
    }

    @Test
    void toolCoversCombatLevelingLocally() {
        StardewKnowledgeRepository repository = new StardewKnowledgeRepository();
        repository.load();
        StardewGuideTool tool = new StardewGuideTool(new StardewGuideService(repository));

        Map<String, Object> result = tool.guide("我战斗等级低，战斗技能如何快速升级");

        assertThat(result).containsEntry("intent", "guide");
        assertThat(result.get("evidence").toString()).contains("击杀怪物", "战士", "野蛮人");
    }

    @Test
    void toolCoversMuseumArtifactAndMineralCompletionLocally() {
        StardewKnowledgeRepository repository = new StardewKnowledgeRepository();
        repository.load();
        StardewGuideTool tool = new StardewGuideTool(new StardewGuideService(repository));

        Map<String, Object> result = tool.guide("博物馆最后缺古物和矿物怎么补");

        assertThat(result).containsEntry("intent", "guide");
        assertThat(result.get("evidence").toString())
                .contains("95 件", "古物宝藏", "晶球", "星之果实");
        assertThat(result).doesNotContainKeys("sourceUrls", "gameVersion", "lastCheckedAt");
    }

    @Test
    void toolCoversCrabPotQuestions() {
        StardewKnowledgeRepository repository = new StardewKnowledgeRepository();
        repository.load();
        StardewGuideTool tool = new StardewGuideTool(new StardewGuideService(repository));

        Map<String, Object> result = tool.guide("蟹笼能抓什么");

        assertThat(result).containsEntry("intent", "fish_available");
        assertThat(result.get("evidence").toString()).contains("龙虾", "小龙虾", "虾", "玉黍螺");
    }

    @Test
    void toolCoversFishingJellyQuestions() {
        StardewKnowledgeRepository repository = new StardewKnowledgeRepository();
        repository.load();
        StardewGuideTool tool = new StardewGuideTool(new StardewGuideService(repository));

        Map<String, Object> result = tool.guide("三种果冻怎么钓");

        assertThat(result).containsEntry("intent", "fish_available");
        assertThat(result.get("evidence").toString()).contains("海果冻", "河果冻", "洞穴果冻", "鱼熏机");
    }

    @Test
    void toolCoversCropQuestions() {
        StardewKnowledgeRepository repository = new StardewKnowledgeRepository();
        repository.load();
        StardewGuideTool tool = new StardewGuideTool(new StardewGuideService(repository));

        Map<String, Object> result = tool.guide("夏季种什么收益好");

        assertThat(result).containsEntry("intent", "crop_available");
        assertThat(result.get("evidence").toString()).contains("杨桃", "蓝莓", "甜瓜", "g/天");
    }

    @Test
    void toolCoversBuildingQuestions() {
        StardewKnowledgeRepository repository = new StardewKnowledgeRepository();
        repository.load();
        StardewGuideTool tool = new StardewGuideTool(new StardewGuideService(repository));

        Map<String, Object> result = tool.guide("豪华畜棚能养什么，多少钱");

        assertThat(result).containsEntry("intent", "building_detail");
        assertThat(result.get("evidence").toString()).contains("豪华畜棚", "25,000g", "猪", "自动喂食");
    }

    @Test
    void toolCoversMachineQuestions() {
        StardewKnowledgeRepository repository = new StardewKnowledgeRepository();
        repository.load();
        StardewGuideTool tool = new StardewGuideTool(new StardewGuideService(repository));

        Map<String, Object> result = tool.guide("鱼熏机需要什么材料，熏鱼值钱吗");

        assertThat(result).containsEntry("intent", "machine_detail");
        assertThat(result.get("evidence").toString()).contains("硬木 x10", "海果冻 x1", "售价为原鱼价格 x2");
    }

    @Test
    void toolCoversCookingBuffQuestions() {
        StardewKnowledgeRepository repository = new StardewKnowledgeRepository();
        repository.load();
        StardewGuideTool tool = new StardewGuideTool(new StardewGuideService(repository));

        Map<String, Object> result = tool.guide("骷髅洞穴吃什么料理 buff 好");

        assertThat(result).containsEntry("intent", "cooking_available");
        assertThat(result.get("evidence").toString()).contains("香辣鳗鱼", "幸运午餐", "三倍浓缩咖啡");
    }
}
