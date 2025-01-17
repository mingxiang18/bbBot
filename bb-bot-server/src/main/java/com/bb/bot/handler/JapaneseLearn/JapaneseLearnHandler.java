package com.bb.bot.handler.JapaneseLearn;

import cn.hutool.core.util.RandomUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.api.BbMessageApi;
import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.constant.BotType;
import com.bb.bot.database.chatHistory.entity.ChatHistory;
import com.bb.bot.database.chatHistory.mapper.ChatHistoryMapper;
import com.bb.bot.database.japaneseLearn.entity.JapaneseFifty;
import com.bb.bot.database.japaneseLearn.mapper.JapaneseFiftyMapper;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.bb.BbSendMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * 日语学习处理者
 * @author ren
 */
@Slf4j
@BootEventHandler(botType = BotType.BB, name = "日语学习相关")
public class JapaneseLearnHandler {

    @Autowired
    private BbMessageApi bbMessageApi;

    @Autowired
    private JapaneseFiftyMapper japaneseFiftyMapper;

    @Autowired
    private ChatHistoryMapper chatHistoryMapper;

    /**
     * 随机日语五十音
     */
    @Rule(eventType = EventType.MESSAGE, ruleType = RuleType.MATCH, keyword = {"jrf", "随机五十音", "随机平假名", "随机片假名", "随机日语音标"}, name = "随机五十音")
    @Transactional(rollbackFor = Exception.class)
    public void japaneseFiftyHandle(BbReceiveMessage bbReceiveMessage) {
        //接收的消息内容
        String groupId = bbReceiveMessage.getGroupId();
        String userId = bbReceiveMessage.getUserId();

        String content = null;

        Long jpFiftyCount = japaneseFiftyMapper.selectCount(null);
        JapaneseFifty japaneseFifty = japaneseFiftyMapper.selectOne(new LambdaQueryWrapper<JapaneseFifty>()
                .last("limit 1 offset " + RandomUtil.randomInt(0, jpFiftyCount.intValue())));

        if ("随机平假名".equals(bbReceiveMessage.getMessage())) {
            content = japaneseFifty.getHiragana();
        }else if ("随机片假名".equals(bbReceiveMessage.getMessage())) {
            content = japaneseFifty.getKatakana();
        }else if ("随机日语音标".equals(bbReceiveMessage.getMessage())) {
            content = japaneseFifty.getPhonetic();
        }else {
            //随机抽取
            int num = RandomUtil.randomInt(0, 3);
            if (num == 0) {
                content = japaneseFifty.getHiragana();
            } else if (num == 1) {
                content = japaneseFifty.getKatakana();
            } else if (num == 2) {
                content = japaneseFifty.getPhonetic();
            }
        }

        //将机器人回复内容也保存到数据库
        ChatHistory chatHistory = new ChatHistory();
        chatHistory.setUserQq("bot");
        chatHistory.setGroupId(bbReceiveMessage.getGroupId());
        chatHistory.setText(JSON.toJSONString(Collections.singletonList(BbMessageContent.buildTextContent(content))));
        chatHistory.setType("jp_fifty");
        chatHistoryMapper.insert(chatHistory);

        //发送消息
        BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
        bbSendMessage.setMessageList(Collections.singletonList(BbMessageContent.buildTextContent(content)));
        bbMessageApi.sendMessage(bbSendMessage);
    }

    /**
     * 获取日语五十音答案
     */
    @Rule(eventType = EventType.MESSAGE, ruleType = RuleType.MATCH, keyword = {"jra", "五十音答案"}, name = "五十音答案")
    public void japaneseFiftyDetailHandle(BbReceiveMessage bbReceiveMessage) {
        //接收的消息内容
        String groupId = bbReceiveMessage.getGroupId();
        String userId = bbReceiveMessage.getUserId();

        //获取最后一条随机五十音的数据
        ChatHistory chatHistory = chatHistoryMapper.selectOne(new LambdaQueryWrapper<ChatHistory>()
                .eq(ChatHistory::getUserQq, "bot")
                .eq(StringUtils.isNoneBlank(groupId), ChatHistory::getUserQq, groupId)
                .eq(ChatHistory::getType, "jp_fifty")
                .orderByDesc(ChatHistory::getCreateTime)
                .last("limit 1"));

        //如果不存在，则不回复
        if (chatHistory == null) {
            return;
        }

        String answer = null;
        //查询答案
        String question = "%" + chatHistory.getText() + "%";
        JapaneseFifty japaneseFifty = japaneseFiftyMapper.selectOne(new LambdaQueryWrapper<JapaneseFifty>()
                .or().like(JapaneseFifty::getHiragana, question)
                .or().like(JapaneseFifty::getKatakana, question)
                .or().like(JapaneseFifty::getPhonetic, question)
                .last("limit 1"));

        if (japaneseFifty == null) {
            answer = "奇怪，没有找到答案呢";
        }else {
            answer = "平假名：" + japaneseFifty.getHiragana() + ", 片假名：" + japaneseFifty.getKatakana() + ", 音标：" + japaneseFifty.getPhonetic() + ", 提示词：" + japaneseFifty.getTips();
        }

        //发送消息
        BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
        bbSendMessage.setMessageList(Collections.singletonList(BbMessageContent.buildTextContent(answer)));
        bbMessageApi.sendMessage(bbSendMessage);
    }

    /**
     * 日语五十音获取
     */
    @Rule(eventType = EventType.MESSAGE, ruleType = RuleType.MATCH, keyword = {"五十音", "五十音清音", "五十音浊音", "五十音拗音"}, name = "五十音获取")
    public void getJapaneseFiftyHandle(BbReceiveMessage bbReceiveMessage) {
        //接收的消息内容
        String groupId = bbReceiveMessage.getGroupId();
        String userId = bbReceiveMessage.getUserId();

        int offset = 0;
        int limit = 92;
        if ("五十音清音".equals(bbReceiveMessage.getMessage())) {
            limit = 46;
        }else if ("五十音浊音".equals(bbReceiveMessage.getMessage())) {
            offset = 46;
            limit = 25;
        }else if ("五十音拗音".equals(bbReceiveMessage.getMessage())) {
            offset = 71;
            limit = 21;
        }

        StringBuilder reply = new StringBuilder();
        //查询相应数据
        List<JapaneseFifty> japaneseFiftyList = japaneseFiftyMapper.selectList(new LambdaQueryWrapper<JapaneseFifty>()
                .last("limit " + limit + " offset " + offset));
        for (JapaneseFifty japaneseFifty : japaneseFiftyList) {
            reply.append(japaneseFifty.getHiragana() + ", " + japaneseFifty.getKatakana() + ", " + japaneseFifty.getPhonetic() + "\n");
        }

        //发送消息
        BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
        bbSendMessage.setMessageList(Collections.singletonList(BbMessageContent.buildTextContent(reply.toString())));
        bbMessageApi.sendMessage(bbSendMessage);
    }
}

