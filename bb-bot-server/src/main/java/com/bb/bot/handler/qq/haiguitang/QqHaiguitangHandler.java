package com.bb.bot.handler.qq.haiguitang;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.api.qq.QqMessageApi;
import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.common.util.aiChat.AiChatClient;
import com.bb.bot.constant.BotType;
import com.bb.bot.database.userConfigInfo.entity.UserConfigValue;
import com.bb.bot.database.userConfigInfo.service.IUserConfigValueService;
import com.bb.bot.entity.qq.ChannelMessage;
import com.bb.bot.entity.qq.QqMessage;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * 海龟汤事件处理器
 * @author ren
 */
@BootEventHandler(botType = BotType.QQ)
public class QqHaiguitangHandler {

    @Autowired
    private QqMessageApi qqMessageApi;

    @Autowired
    private IUserConfigValueService userConfigValueService;

    @Autowired
    private AiChatClient aiChatClient;

    @Value("${haiguitang.personality:你是一个海龟汤裁判，请根据海龟汤题目、正确答案和玩家的猜测进行回答。\n" +
            "## 关于海龟汤\n" +
            "海龟汤是一种猜测情境型事件真相的智力游戏。其玩法是由出题者提出一个难以理解的事件，参与猜题者可以提出任何问题以试图缩小范围并找出事件背后真正的原因，但裁判仅能以“是”、“不是”、或“不相关”来回答问题。\n" +
            "现在有一个问题和答案如下：\" +\n" +
            "问题：{question}\n" +
            "答案：{answer}\n" +
            " ## 接下来猜题者会给你提出问题，请根据海龟汤题目、正确答案和玩家的猜测进行回答进行回答，要求\n" +
            " 1. **你只能回答“是”、“不是”、“不相关”、“成功”来回答问题。**“是”和“不是”表示猜测符合答案的情景，“不相关”表示无论你的回答是“是”或者“不是”都与答案情景无关，“成功”表示已经完全猜出了答案，可以结束本局游戏。\n" +
            " 2. 不要输出任何其他文字和标点符号。\n" +
            " 3. 不要输出非以下范围内的内容：“是”、“不是”、“不相关”、“成功”。不要输出“是的”、“否”、“没有”等等。\\n" +
            " 4. 除非是严重脱离情景，否则尽量少回答“不相关”。\n")
    private String botPersonality;

    @SneakyThrows
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH, keyword = {"海龟汤", "/海龟汤"}, name = "海龟汤")
    public void haiguitangStartHandle(QqMessage event) {
        //随机取一条
        UserConfigValue haiguitangConfig = userConfigValueService.getOne(new LambdaQueryWrapper<UserConfigValue>()
                .eq(UserConfigValue::getUserId, "system")
                .eq(UserConfigValue::getType, "haiguitang")
                .last("ORDER BY RAND() LIMIT 1"));
        if (haiguitangConfig == null) {
            ChannelMessage channelMessage = new ChannelMessage();
            channelMessage.setContent("不存在海龟汤问题");
            channelMessage.setMsgId(event.getId());
            qqMessageApi.sendChannelMessage(event.getChannelId(), channelMessage);
            return;
        }

        String[] haiguitang = haiguitangConfig.getValueName().split("%n%");
        String question = haiguitang[0];
        String answer = haiguitang[1];

        //先获取是否有占用
        if (userConfigValueService.getOne(new LambdaQueryWrapper<UserConfigValue>()
                .eq(UserConfigValue::getGroupId, event.getChannelId())
                .eq(UserConfigValue::getType, RuleType.OCCUPATION)) == null) {
            //没有占用则保存占用
            UserConfigValue occupationConfig = new UserConfigValue();
            occupationConfig.setGroupId(event.getChannelId());
            occupationConfig.setType(RuleType.OCCUPATION);
            occupationConfig.setKeyName(this.getClass().getDeclaredMethod("haiguitangJudgeHandle", QqMessage.class).getName());
            occupationConfig.setValueName(haiguitangConfig.getId().toString());
            userConfigValueService.save(occupationConfig);
        }else {
            ChannelMessage channelMessage = new ChannelMessage();
            channelMessage.setContent("当前正在进行游戏中，无法开始海龟汤噢");
            channelMessage.setMsgId(event.getId());
            qqMessageApi.sendChannelMessage(event.getChannelId(), channelMessage);
        }

        ChannelMessage channelMessage = new ChannelMessage();
        channelMessage.setContent("汤面如下：" + question + "\n发送【海龟汤答案】可以结束本次游戏噢。");
        channelMessage.setMsgId(event.getId());
        qqMessageApi.sendChannelMessage(event.getChannelId(), channelMessage);
    }

    @SneakyThrows
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.OCCUPATION, name = "海龟汤猜谜")
    public void haiguitangJudgeHandle(QqMessage event) {
        //获取当前群聊配置的问题
        UserConfigValue groupConfig = userConfigValueService.getOne(new LambdaQueryWrapper<UserConfigValue>()
                .eq(UserConfigValue::getGroupId, event.getChannelId())
                .eq(UserConfigValue::getType, RuleType.OCCUPATION)
                .eq(UserConfigValue::getKeyName, this.getClass().getDeclaredMethod("haiguitangJudgeHandle", QqMessage.class).getName()));
        if (groupConfig == null) {
            //删除占用
            userConfigValueService.remove(new LambdaQueryWrapper<UserConfigValue>()
                    .eq(UserConfigValue::getGroupId, event.getChannelId())
                    .eq(UserConfigValue::getType, RuleType.OCCUPATION));

            ChannelMessage channelMessage = new ChannelMessage();
            channelMessage.setContent("当前没有海龟汤问题");
            channelMessage.setMsgId(event.getId());
            qqMessageApi.sendChannelMessage(event.getChannelId(), channelMessage);
            return;
        }

        //获取指定问题
        UserConfigValue haiguitangConfig = userConfigValueService.getOne(new LambdaQueryWrapper<UserConfigValue>()
                .eq(UserConfigValue::getId, Long.valueOf(groupConfig.getValueName())));
        if (haiguitangConfig == null) {
            //删除占用
            userConfigValueService.remove(new LambdaQueryWrapper<UserConfigValue>()
                    .eq(UserConfigValue::getGroupId, event.getChannelId())
                    .eq(UserConfigValue::getType, RuleType.OCCUPATION));

            ChannelMessage channelMessage = new ChannelMessage();
            channelMessage.setContent("不存在海龟汤问题");
            channelMessage.setMsgId(event.getId());
            qqMessageApi.sendChannelMessage(event.getChannelId(), channelMessage);
            return;
        }

        String[] haiguitang = haiguitangConfig.getValueName().split("%n%");
        String question = haiguitang[0];
        String answer = haiguitang[1];

        if (event.getContent().contains("海龟汤答案")) {
            //删除占用
            userConfigValueService.remove(new LambdaQueryWrapper<UserConfigValue>()
                    .eq(UserConfigValue::getGroupId, event.getChannelId())
                    .eq(UserConfigValue::getType, RuleType.OCCUPATION));

            ChannelMessage channelMessage = new ChannelMessage();
            channelMessage.setContent("汤底如下噢：" + answer);
            channelMessage.setMsgId(event.getId());
            qqMessageApi.sendChannelMessage(event.getChannelId(), channelMessage);
        }else {
            //询问chatGPT获取答案
            String fullPersonality = botPersonality.replace("{question}", question).replace("{answer}", answer);
            String botAnswer = aiChatClient.askChatGPT(fullPersonality, event.getContent(), null);

            ChannelMessage channelMessage = new ChannelMessage();
            channelMessage.setContent(botAnswer);
            channelMessage.setMsgId(event.getId());
            qqMessageApi.sendChannelMessage(event.getChannelId(), channelMessage);
        }

    }

    @SneakyThrows
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH, keyword = {"生成海龟汤", "/生成海龟汤"}, name = "生成海龟汤")
    public void haiguitangGenerateHandle(QqMessage event) {
        String personality = "你是一个海龟汤出题者，请帮我出一道海龟汤题目。\n" +
                "## 海龟汤题目要求\n" +
                "1. 题目情景应当有创意，有适当的难度。\n" +
                "2. **重要：不要类似于脑筋急转弯**，也就是说答案不要过于简单。但**问题应当简短，以两三句话左右为宜**，留出一些猜测的空间。\n" +
                "3. 情景应当稍微恐怖一点，通常要死人。\n" +
                "4. **重要：题目情景和答案一定要足够巧妙**，令人拍案叫绝，不要无聊，要有趣。但同时必须符合逻辑。最好有一条故事线，但不要太长。\n" +
                "5. 你可以参考推理小说、侦探小说等等。\n" +
                "6. 在设计题目前，请提供你的思路，例如故事情节、灵感来源、题目巧妙之处。\n" +
                "7. 请以如下格式输出：\n" +
                "{{\n" +
                "    \"问题\": ...,\n" +
                "    \"答案\": ...\n" +
                "}}\n" +
                "8. 题目要有趣！要有趣！！要有趣！！！\n" +
                "9. 题目需要自洽，要**符合逻辑**。\n" +
                "10. 题目的难度要适中，既不要过于简单，也不要太过于困难。\n" +
                "11. 避免在问题中出现不必要的信息，确保问题中每个信息都与答案中的情景相关，构成一条完整的故事线。\n" +
                "12. 故事不必有科幻元素。不要包含价值观导向或者深刻的内涵，只需要是个有趣的故事，就像推理小说一样。";

        String answer = aiChatClient.askChatGPT("你是一个海龟汤出题者，请帮我出一道海龟汤题目。", personality, null);

        ChannelMessage channelMessage = new ChannelMessage();
        channelMessage.setContent(answer);
        channelMessage.setMsgId(event.getId());
        qqMessageApi.sendChannelMessage(event.getChannelId(), channelMessage);
    }

    @SneakyThrows
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH, keyword = {"设置海龟汤", "/设置海龟汤"}, name = "设置海龟汤")
    public void setHaiguitangHandle(QqMessage event) {
        if (!event.getContent().contains("%n%")) {
            ChannelMessage channelMessage = new ChannelMessage();
            channelMessage.setContent("海龟汤格式不符合要求");
            channelMessage.setMsgId(event.getId());
            qqMessageApi.sendChannelMessage(event.getChannelId(), channelMessage);
        }

        UserConfigValue haiguitangConfig = new UserConfigValue();
        haiguitangConfig.setUserId("system");
        haiguitangConfig.setType("haiguitang");
        haiguitangConfig.setKeyName("1");
        haiguitangConfig.setValueName(event.getContent());
        userConfigValueService.save(haiguitangConfig);

        ChannelMessage channelMessage = new ChannelMessage();
        channelMessage.setContent("设置完成");
        channelMessage.setMsgId(event.getId());
        qqMessageApi.sendChannelMessage(event.getChannelId(), channelMessage);
    }
}
