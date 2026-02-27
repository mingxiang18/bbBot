package com.bb.bot.handler.chatHistory;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.bb.bot.api.BbMessageApi;
import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.common.constant.SyncType;
import com.bb.bot.common.util.aiChat.AiChatClient;
import com.bb.bot.common.util.aiChat.ChatGPTContent;
import com.bb.bot.constant.BbSendMessageType;
import com.bb.bot.constant.BotType;
import com.bb.bot.constant.MessageType;
import com.bb.bot.database.chatHistory.entity.ChatHistory;
import com.bb.bot.database.chatHistory.mapper.ChatHistoryMapper;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.bb.BbSendMessage;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 聊天历史记录处理器
 * 用于记录所有聊天消息
 * @author ren
 */
@BootEventHandler(botType = BotType.BB, order = 1, name = "聊天历史记录")
public class BbChatHistoryHandler {

    @Autowired
    private BbMessageApi bbMessageApi;

    @Autowired
    private ChatHistoryMapper chatHistoryMapper;

    @Autowired
    private AiChatClient aiChatClient;

    /**
     * ai回复需要携带的历史记录数量
     */
    @Value("${chatHistory.summary.chatHistoryNum:100}")
    private int chatHistoryNum;

    /**
     * 总结聊天记录的设定
     */
    @Value("${chatHistory.summary.aiPersonality:你是一个群聊消息的总结助手，请根据用户发送的聊天记录，对所有记录的要点进行分类总结，不能粗略将事件总结为做了某事，要具体到事件内容}")
    private String chatHistorySummaryPersonality;

    /**
     * 提取聊天记录线索的设定
     */
    @Value("${chatHistory.characteristic.aiPersonality:你是一个聊天线索提取机器人，请根据用户发送的聊天记录，" +
            "提取出聊天记录中的主要线索，线索分为用户线索和事件线索。" +
            "聊天记录中:符号前面表示聊天用户名，:符号后面表示用户聊天内容，句号代表一条聊天记录的结束" +
            "提取出的线索按照以下json示例格式进行输出" +
            "[{\n" +
            "    \"keyword\": [\"关键字1\", \"关键字2\"],\n" +
            "    \"content\": \"用户xxx为游戏玩家，可能是一个游戏达人，经常关注游戏内的活动和奖励\",\n" +
            "    \"weight\": 2\n" +
            "}]\n" +
            "上面的json只是示例！！！具体线索和示例不一样，请不要直接输出示例，请从聊天记录中进行提取线索后输出。" +
            "用户线索重要的点在于提取每个用户的性格和特征，事件线索重要的点是提取具体事件内容" +
            "每个说过话的用户都要有对应的用户线索，用户线索的关键字只能为单个用户的用户名，不能多个用户混合到一个线索的关键字！" +
            "用户所讨论的特殊事件要输出相关事件线索，有相关性的事件线索线索内容进行整合，通过多个关键字关联，只记录有特殊含义的关键字和事件，比如关键字“喷喷”，线索内容为“是一款叫斯普拉遁3的体感射击游戏”，大家都知道的普通内容不用输出，相关性不大的事件线索单独输出" +
            "注意：示例中的keyword表示关键字，一条线索可以有多个关键字，比如一个线索表示用户/事件的描述，这个用户/事件可以多个别名，表示同一条线索。" +
            "content表示线索详情，线索详情是从聊天记录中整合推断而出，请不要照搬聊天记录内容，weight表示权重，每条线索从1开始计算，每次关键字出现时，权重加1" +
            "请尽可能详细分析，输出尽可能多的线索，每个说过话的用户都要有对应的用户线索！用户所讨论的每个事件都要输出相关事件线索！}")
    private String chatHistoryCharacteristicPersonality;

    @Rule(eventType = EventType.MESSAGE, name = "聊天历史记录, 用于记录所有聊天消息", syncType = SyncType.SYNC)
    public void chatHistoryHandle(BbReceiveMessage bbReceiveMessage) {
        //过滤掉本地图片，因为数据太大
        List<BbMessageContent> bbMessageContentList = bbReceiveMessage.getMessageContentList()
                .stream()
                .filter(bbMessageContent -> !BbSendMessageType.LOCAL_IMAGE.equals(bbMessageContent.getType()))
                .toList();
        //如果为空则不记录聊天消息
        if (CollectionUtils.isEmpty(bbMessageContentList)) {
            return;
        }

        ChatHistory chatHistory = new ChatHistory();
        chatHistory.setMessageId(bbReceiveMessage.getMessageId());
        chatHistory.setUserQq(bbReceiveMessage.getUserId());
        chatHistory.setUserName(bbReceiveMessage.getSender() == null ? null : bbReceiveMessage.getSender().getNickname());
        chatHistory.setGroupId(bbReceiveMessage.getGroupId());
        chatHistory.setPrivateUserId(bbReceiveMessage.getUserId());
        chatHistory.setText(JSON.toJSONString(bbMessageContentList));
        chatHistoryMapper.insert(chatHistory);
    }

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH, keyword = {"聊天记录总结", "/聊天记录总结"}, name = "近期聊天记录总结")
    public void chatHistorySummaryHandle(BbReceiveMessage bbReceiveMessage) {
        //如果没有配置ai，则返回不支持
        if (!aiChatClient.hasConfigAI()) {
            //发送消息
            BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
            bbSendMessage.setMessageList(Arrays.asList(
                    BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
                    BbMessageContent.buildTextContent("机器人当前暂不支持聊天记录总结"))
            );
            bbMessageApi.sendMessage(bbSendMessage);
            return;
        }

        //查询历史记录
        List<ChatHistory> chatHistoryList = new ArrayList<>();
        if (MessageType.GROUP.equals(bbReceiveMessage.getMessageType()) || MessageType.CHANNEL.equals(bbReceiveMessage.getMessageType())) {
            //群组
            chatHistoryList = chatHistoryMapper.selectList(new LambdaQueryWrapper<ChatHistory>()
                            .eq(ChatHistory::getGroupId, bbReceiveMessage.getGroupId())
                            .orderByDesc(ChatHistory::getCreateTime)
                            .last("limit " + chatHistoryNum))
                    .stream().sorted(Comparator.comparing(ChatHistory::getCreateTime)).collect(Collectors.toList());
        }else if (MessageType.PRIVATE.equals(bbReceiveMessage.getMessageType())) {
            chatHistoryList = chatHistoryMapper.selectList(new LambdaQueryWrapper<ChatHistory>()
                            .isNull(ChatHistory::getGroupId)
                            .eq(ChatHistory::getPrivateUserId, bbReceiveMessage.getUserId())
                            .orderByDesc(ChatHistory::getCreateTime)
                            .last("limit " + chatHistoryNum))
                    .stream().sorted(Comparator.comparing(ChatHistory::getCreateTime)).collect(Collectors.toList());
        }
        if (CollectionUtils.isEmpty(chatHistoryList)) {
            //发送消息
            BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
            bbSendMessage.setMessageList(Arrays.asList(
                    BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
                    BbMessageContent.buildTextContent("当前暂无聊天记录可总结"))
            );
            bbMessageApi.sendMessage(bbSendMessage);
            return;
        }

        //从历史记录构建ai模型请求体
        List<ChatGPTContent> chatContentList = buildChatContentList(chatHistorySummaryPersonality, chatHistoryList);
        //调用ai模型获取请求
        String answer = aiChatClient.askChatGPT(chatContentList);

        //保存机器人回复
        ChatHistory chatHistory = new ChatHistory();
        chatHistory.setMessageId(IdWorker.getIdStr());
        chatHistory.setUserQq("bot");
        chatHistory.setGroupId(bbReceiveMessage.getGroupId());
        chatHistory.setText(JSON.toJSONString(Collections.singletonList(BbMessageContent.buildTextContent(answer))));
        chatHistoryMapper.insert(chatHistory);

        //发送消息
        BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
        bbSendMessage.setMessageList(Arrays.asList(
                BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
                BbMessageContent.buildTextContent("\n" + answer))
        );
        bbMessageApi.sendMessage(bbSendMessage);
    }

//    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH, keyword = {"提取聊天线索", "/提取聊天线索"}, name = "通过ai提取聊天线索")
    public void chatHistoryCharacteristicHandle(BbReceiveMessage bbReceiveMessage) {
        //如果没有配置ai，则返回不支持
        if (!aiChatClient.hasConfigAI()) {
            //发送消息
            BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
            bbSendMessage.setMessageList(Arrays.asList(
                    BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
                    BbMessageContent.buildTextContent("机器人当前暂不支持提取聊天线索"))
            );
            bbMessageApi.sendMessage(bbSendMessage);
            return;
        }

        //查询历史记录
        List<ChatHistory> chatHistoryList = new ArrayList<>();
        if (MessageType.GROUP.equals(bbReceiveMessage.getMessageType()) || MessageType.CHANNEL.equals(bbReceiveMessage.getMessageType())) {
            //群组
            chatHistoryList = chatHistoryMapper.selectList(new LambdaQueryWrapper<ChatHistory>()
                            .eq(ChatHistory::getGroupId, bbReceiveMessage.getGroupId())
                            .orderByDesc(ChatHistory::getCreateTime)
                            .last("limit " + chatHistoryNum))
                    .stream().sorted(Comparator.comparing(ChatHistory::getCreateTime)).collect(Collectors.toList());
        }else if (MessageType.PRIVATE.equals(bbReceiveMessage.getMessageType())) {
            chatHistoryList = chatHistoryMapper.selectList(new LambdaQueryWrapper<ChatHistory>()
                            .isNull(ChatHistory::getGroupId)
                            .orderByDesc(ChatHistory::getCreateTime)
                            .last("limit " + chatHistoryNum))
                    .stream().sorted(Comparator.comparing(ChatHistory::getCreateTime)).collect(Collectors.toList());
        }
        if (CollectionUtils.isEmpty(chatHistoryList)) {
            //发送消息
            BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
            bbSendMessage.setMessageList(Arrays.asList(
                    BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
                    BbMessageContent.buildTextContent("当前暂无聊天记录可提取线索"))
            );
            bbMessageApi.sendMessage(bbSendMessage);
            return;
        }

        //从历史记录构建ai模型请求体
        List<ChatGPTContent> chatContentList = buildChatContentList(chatHistoryCharacteristicPersonality, chatHistoryList);
        //调用ai模型获取请求
        String answer = aiChatClient.askChatGPT(chatContentList);

        //保存机器人回复
        ChatHistory chatHistory = new ChatHistory();
        chatHistory.setMessageId(IdWorker.getIdStr());
        chatHistory.setUserQq("bot");
        chatHistory.setGroupId(bbReceiveMessage.getGroupId());
        chatHistory.setText(JSON.toJSONString(Collections.singletonList(BbMessageContent.buildTextContent(answer))));
        chatHistoryMapper.insert(chatHistory);

        //发送消息
        BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
        bbSendMessage.setMessageList(Arrays.asList(
                BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
                BbMessageContent.buildTextContent("\n" + answer))
        );
        bbMessageApi.sendMessage(bbSendMessage);
    }

    /**
     * 从历史消息构建ai模型请求体
     */
    private List<ChatGPTContent> buildChatContentList(String personality, List<ChatHistory> chatHistoryList) {
        List<ChatGPTContent> chatContentList = new ArrayList<>();
        //构建ai角色
        chatContentList.add(new ChatGPTContent(ChatGPTContent.SYSTEM_ROLE, personality));
        //从历史聊天记录构建请求
        chatContentList.add(new ChatGPTContent(ChatGPTContent.USER_ROLE,
                chatHistoryList.stream().map(chatHistory -> {
                    String userName = (StringUtils.isBlank(chatHistory.getUserName()) ? chatHistory.getUserQq() : chatHistory.getUserName()) + ":";
                    try {
                        List<BbMessageContent> contentList = JSON.parseObject(chatHistory.getText(), new TypeReference<List<BbMessageContent>>() {});
                        return userName + contentList.stream()
                                //图片或者回复消息跳过
                                .filter(bbMessageContent ->
                                        !BbSendMessageType.LOCAL_IMAGE.equals(bbMessageContent.getType()) &&
                                                !BbSendMessageType.NET_IMAGE.equals(bbMessageContent.getType()) &&
                                                !BbSendMessageType.REPLY.equals(bbMessageContent.getType()))
                                //将内容转字符串后拼接
                                .map(bbMessageContent -> {
                                    if (BbSendMessageType.AT.equals(bbMessageContent.getType())) {
                                        return "@" + bbMessageContent.getData().toString();
                                    }else {
                                        return bbMessageContent.getData().toString();
                                    }
                                })
                                .collect(Collectors.joining(" "));
                    }catch (Exception e) {
                        return userName + chatHistory.getText();
                    }
                }).collect(Collectors.joining("。"))));
        return chatContentList;
    }
}
