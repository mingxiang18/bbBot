package com.bb.bot.handler.aiChat;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.bb.bot.api.BbMessageApi;
import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.common.util.aiChat.AiChatClient;
import com.bb.bot.constant.BotType;
import com.bb.bot.constant.MessageType;
import com.bb.bot.database.aiKeywordAndClue.service.IAiClueService;
import com.bb.bot.database.chatHistory.entity.ChatHistory;
import com.bb.bot.database.chatHistory.service.IChatHistoryService;
import com.bb.bot.database.userConfigInfo.entity.UserConfigValue;
import com.bb.bot.database.userConfigInfo.service.IUserConfigValueService;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.bb.BbSendMessage;
import com.bb.bot.database.aiKeywordAndClue.vo.ClueDetail;
import com.bb.bot.entity.bb.MessageUser;
import org.apache.http.util.Asserts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * ai聊天事件处理器
 * @author ren
 */
@BootEventHandler(botType = BotType.BB, name = "AI聊天")
public class BbAiChatHandler {

    private static final Logger log = LoggerFactory.getLogger(BbAiChatHandler.class);
    @Autowired
    private BbMessageApi bbMessageApi;

    @Autowired
    private AiChatClient aiChatClient;

    @Autowired
    private IChatHistoryService chatHistoryService;

    @Autowired
    private IAiClueService aiClueService;

    @Autowired
    private IUserConfigValueService userConfigValueService;

    /**
     * chatGPT的性格
     */
    @Value("${chatGPT.personality:你的名字是冥想bb，你是一只充满活力和可爱的鱿鱼偶像。你在一个充满零碎消息的群聊中，请根据以下指示进行回复：\n" +
            "1. **判断上下文**：请仔细阅读群聊中的消息，判断是否有人提出了问题。如果有人提问，请从专业的角度进行准确、详细地解答；如果没有，请以自然聊天的方式进行回复。\n" +
            "2. **表现风格**：回复时请使用充满偶像活力和可爱的方式。使用颜文字让你的回复更加生动有趣。不要机械地重复回答，也不要模棱两可，给出明确的观点。}")
    private String chatGPTPersonality;

    /**
     * ai回复需要携带的历史记录数量
     */
    @Value("${aiChat.chatHistoryNum:10}")
    private int chatHistoryNum;

    /**
     * ai自动回复概率
     */
    @Value("${aiChat.autoReplyRate:0.99}")
    private Double autoReplyRate;

    @Rule(eventType = EventType.MESSAGE, ruleType = RuleType.DEFAULT, name = "ai自动回复")
    public void aiChatHandle(BbReceiveMessage bbReceiveMessage) {
        //线索列表
        List<String> clueList = new ArrayList<>();
        //是否回复
        boolean isReply = false;

        //如果是私人消息必然触发
        if (MessageType.PRIVATE.equals(bbReceiveMessage.getMessageType())) {
            isReply = true;
        }else if (MessageType.GROUP.equals(bbReceiveMessage.getMessageType())) {
            //如果是群聊消息判断是否被@
            Optional<MessageUser> atMeFlag = bbReceiveMessage.getAtUserList().stream().filter(MessageUser::getBotFlag).findFirst();
            if (atMeFlag.isPresent()) {
                //如果被@则回复
                isReply = true;
                //获取线索
                clueList = aiClueService.selectClue((bbReceiveMessage.getSender() == null ? "" : bbReceiveMessage.getSender().getNickname() + "：") +
                        bbReceiveMessage.getMessage());
            }else {
                //如果没有被@，查询群聊是否配置自动回复
                UserConfigValue configValue = userConfigValueService.getOne(new LambdaQueryWrapper<UserConfigValue>()
                        .eq(UserConfigValue::getGroupId, bbReceiveMessage.getGroupId())
                        .eq(UserConfigValue::getType, "AI")
                        .eq(UserConfigValue::getKeyName, "aiAutoReply")
                        .eq(UserConfigValue::getValueName, "1")
                        .last("limit 1"));

                if (configValue != null) {
                    //判断用户讨论内容是否触发关键字
                    clueList = aiClueService.selectClue((bbReceiveMessage.getSender() == null ? "" : bbReceiveMessage.getSender().getNickname() + "：") +
                            bbReceiveMessage.getMessage());
                    //如果触发关键字，且概率大于自动回复概率，则开始自动回复
                    double replyRate = new Random().nextDouble();
                    log.info("回复概率：" + replyRate);
                    if (!CollectionUtils.isEmpty(clueList) && replyRate > autoReplyRate) {
                        isReply = true;
                    }
                }
            }
        }

        //不回复则结束
        if (!isReply) {
            return;
        }

        //查询历史记录
        List<ChatHistory> chatHistoryList = new ArrayList<>();
        if (MessageType.GROUP.equals(bbReceiveMessage.getMessageType())) {
            //群组
            chatHistoryList = chatHistoryService.list(new LambdaQueryWrapper<ChatHistory>()
                            .eq(ChatHistory::getGroupId, bbReceiveMessage.getGroupId())
                            .orderByDesc(ChatHistory::getCreateTime)
                            .last("limit " + chatHistoryNum))
                    .stream().sorted(Comparator.comparing(ChatHistory::getCreateTime)).collect(Collectors.toList());
        }else if (MessageType.PRIVATE.equals(bbReceiveMessage.getMessageType())) {
            chatHistoryList = chatHistoryService.list(new LambdaQueryWrapper<ChatHistory>()
                            .isNull(ChatHistory::getGroupId)
                            .orderByDesc(ChatHistory::getCreateTime)
                            .last("limit " + chatHistoryNum))
                    .stream().sorted(Comparator.comparing(ChatHistory::getCreateTime)).collect(Collectors.toList());
        }

        String personality = chatGPTPersonality;
        if (!CollectionUtils.isEmpty(clueList)) {
            personality = personality +
                    "3. **使用记忆**：你有以下事件记忆，请优先基于记忆中的内容进行回复。回复中要带有以前记忆中谁做过对应的某件什么事，但要指明是“曾经讨论”或“曾经出现”，以强调是过去的事件。例如，如果有人提到某个事件，你可以说：“曾经讨论过这个话题呢，xx当时说...” 或者 “这个问题曾经出现过哦，xx做了...”\n" +
                    "记忆如下：" + String.join("-", clueList);
        }

        String answer = aiChatClient.askChatGPT(personality,
                (bbReceiveMessage.getSender() == null ? "" : bbReceiveMessage.getSender().getNickname() + "：") + bbReceiveMessage.getMessage(),
                chatHistoryList);

        //保存机器人回复
        ChatHistory chatHistory = new ChatHistory();
        chatHistory.setMessageId(IdWorker.getIdStr());
        chatHistory.setUserQq("bot");
        chatHistory.setGroupId(bbReceiveMessage.getGroupId());
        chatHistory.setText(answer);
        chatHistoryService.save(chatHistory);

        //发送消息
        BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
        bbSendMessage.setMessageList(Collections.singletonList(BbMessageContent.buildTextContent(answer)));
        bbMessageApi.sendMessage(bbSendMessage);
    }

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH, keyword = {"获取聊天线索", "/获取聊天线索"}, name = "获取聊天线索")
    public void chatHistoryClueHandle(BbReceiveMessage bbReceiveMessage) {
        //todo 暂时没做权限，只判断是自己用的
        if (!bbReceiveMessage.getUserId().equals("1105048721")) {
            //发送消息
            BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
            bbSendMessage.setMessageList(Arrays.asList(
                    BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
                    BbMessageContent.buildTextContent("您当前不具备该权限噢"))
            );
            bbMessageApi.sendMessage(bbSendMessage);
            return;
        }

        List<ClueDetail> clueDetailList = aiClueService.getClueDetailList();
        //发送消息
        BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
        bbSendMessage.setMessageList(Arrays.asList(
                BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
                BbMessageContent.buildTextContent("\n" + JSON.toJSONString(clueDetailList)))
        );
        bbMessageApi.sendMessage(bbSendMessage);
    }

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX, keyword = {"^/?导入线索\\s?"}, name = "导入线索")
    public void importClueHandle(BbReceiveMessage bbReceiveMessage) {
        // 定义正则表达式模式
        Pattern pattern = Pattern.compile("导入线索([\\s\\S]*)");
        Matcher matcher = pattern.matcher(bbReceiveMessage.getMessage());
        String clue = null;
        // 如果找到匹配项
        if (matcher.find()) {
            clue = matcher.group(1);
        }

        List<ClueDetail> clueDetailList = new ArrayList<>();
        try {
            Asserts.notNull(clue, "线索不能为空");
            clueDetailList = JSON.parseObject(clue, new TypeReference<List<ClueDetail>>() {});
        }catch (Exception e) {
            //发送消息
            BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
            bbSendMessage.setMessageList(Arrays.asList(
                    BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
                    BbMessageContent.buildTextContent("线索格式不正确"))
            );
            bbMessageApi.sendMessage(bbSendMessage);
            return;
        }

        //导入线索
        aiClueService.importGroupClue(bbReceiveMessage.getGroupId(), clueDetailList);

        //发送消息
        BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
        bbSendMessage.setMessageList(Arrays.asList(
                BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
                BbMessageContent.buildTextContent("导入成功"))
        );
        bbMessageApi.sendMessage(bbSendMessage);
    }

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX, keyword = {"^/?删除线索\\s?"}, name = "删除线索")
    public void deleteClueHandle(BbReceiveMessage bbReceiveMessage) {
        // 定义正则表达式模式
        Pattern pattern = Pattern.compile("^/?删除线索(\\d+)");
        Matcher matcher = pattern.matcher(bbReceiveMessage.getMessage());
        String clueId = null;
        // 如果找到匹配项
        if (matcher.find()) {
            clueId = matcher.group(1);
        }

        try {
            boolean deleted = aiClueService.deleteClue(Long.parseLong(clueId));
        }catch (Exception e) {
            log.error("删除线索失败", e);
            //发送消息
            BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
            bbSendMessage.setMessageList(Arrays.asList(
                    BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
                    BbMessageContent.buildTextContent("删除失败，格式不正确"))
            );
            bbMessageApi.sendMessage(bbSendMessage);
            return;
        }

        //发送消息
        BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
        bbSendMessage.setMessageList(Arrays.asList(
                BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
                BbMessageContent.buildTextContent("删除成功"))
        );
        bbMessageApi.sendMessage(bbSendMessage);
    }
}
