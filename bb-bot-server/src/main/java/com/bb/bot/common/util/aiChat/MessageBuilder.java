package com.bb.bot.common.util.aiChat;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.bb.bot.common.util.aiChat.provider.ChatMessage;
import com.bb.bot.common.util.aiChat.provider.MessageContent;
import com.bb.bot.constant.BbSendMessageType;
import com.bb.bot.database.chatHistory.entity.ChatHistory;
import com.bb.bot.entity.bb.BbMessageContent;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 把 BB 通用消息体（{@link BbMessageContent}、{@link ChatHistory}）翻译成
 * provider-agnostic 的 {@link ChatMessage} 列表。
 *
 * <p>原本散落在 {@code BbAiChatHandler.buildChatContentList} 与
 * {@code BbChatHistoryHandler.buildChatContentList} 里的几乎相同逻辑被合并到这里。
 *
 * @author ren
 */
public final class MessageBuilder {

    public static final String BOT_USER_FLAG = "bot";

    private MessageBuilder() {}

    /**
     * 用于 AI 聊天 handler：保留每条历史为独立消息，最后一条 user 消息为本轮提问（含图片）。
     */
    public static List<ChatMessage> buildContextMessages(
            String personality,
            List<BbMessageContent> currentMessage,
            List<ChatHistory> history,
            ChatHistory replyTarget) {
        List<ChatMessage> messages = new ArrayList<>();

        if (StringUtils.isNotBlank(personality)) {
            messages.add(ChatMessage.system(personality));
        }

        if (!CollectionUtils.isEmpty(history)) {
            messages.addAll(historyToMessages(filterDuplicateOfCurrent(history, currentMessage)));
        }

        messages.add(buildAskMessage(currentMessage, replyTarget));
        return messages;
    }

    /**
     * 用于 ChatHistory 总结类 handler：所有历史压成一条 user 消息（节省 tokens）。
     */
    public static List<ChatMessage> buildSummaryMessages(String personality, List<ChatHistory> history) {
        List<ChatMessage> messages = new ArrayList<>();
        if (StringUtils.isNotBlank(personality)) {
            messages.add(ChatMessage.system(personality));
        }
        if (CollectionUtils.isEmpty(history)) {
            return messages;
        }
        String joined = history.stream()
                .map(MessageBuilder::historyAsLine)
                .collect(Collectors.joining("。"));
        messages.add(ChatMessage.user(joined));
        return messages;
    }

    /** 把单条历史渲染为 "昵称：文本" 形式（用于总结）。 */
    public static String historyAsLine(ChatHistory chatHistory) {
        String userName = StringUtils.isBlank(chatHistory.getUserName())
                ? chatHistory.getUserQq()
                : chatHistory.getUserName();
        return userName + ":" + extractTextContent(chatHistory);
    }

    /**
     * 历史列表 → ChatMessage 列表。bot 角色映射 ASSISTANT，其它映射 USER 并附昵称前缀。
     */
    public static List<ChatMessage> historyToMessages(List<ChatHistory> history) {
        List<ChatMessage> messages = new ArrayList<>(history.size());
        for (ChatHistory item : history) {
            String text = extractTextContent(item);
            if (BOT_USER_FLAG.equals(item.getUserQq())) {
                messages.add(ChatMessage.assistant(text));
            } else {
                String name = StringUtils.isBlank(item.getUserName()) ? item.getUserQq() : item.getUserName();
                messages.add(ChatMessage.user(name + "：" + text));
            }
        }
        return messages;
    }

    /**
     * 从 {@link ChatHistory#getText()} 里抽出可读文本：旧记录是裸字符串，新记录是
     * {@code List<BbMessageContent>} 的 JSON。两种情况都兼容。本地图片 / 网络图片 /
     * 引用消息会被过滤掉，AT 转成 "@昵称"。
     */
    public static String extractTextContent(ChatHistory chatHistory) {
        String raw = chatHistory.getText();
        if (raw == null) {
            return "";
        }
        try {
            List<BbMessageContent> contents = JSON.parseObject(raw, new TypeReference<List<BbMessageContent>>() {});
            if (contents == null) {
                return raw;
            }
            return contents.stream()
                    .filter(MessageBuilder::keepForText)
                    .map(MessageBuilder::stringifyPart)
                    .collect(Collectors.joining(" "));
        } catch (Exception ignored) {
            return raw;
        }
    }

    /** 把当前轮的多 part 消息（可能含图）+ 引用消息中的图片合成一条 user 消息。 */
    public static ChatMessage buildAskMessage(List<BbMessageContent> currentMessage, ChatHistory replyTarget) {
        List<MessageContent> parts = new ArrayList<>();

        if (replyTarget != null) {
            try {
                List<BbMessageContent> replyContents = JSON.parseObject(
                        replyTarget.getText(), new TypeReference<List<BbMessageContent>>() {});
                if (replyContents != null) {
                    for (BbMessageContent c : replyContents) {
                        if (BbSendMessageType.NET_IMAGE.equals(c.getType())) {
                            parts.add(MessageContent.netImage(String.valueOf(c.getData())));
                        }
                    }
                }
            } catch (Exception ignored) {
                // 引用消息解析失败不影响主流程
            }
        }

        StringBuilder askText = new StringBuilder();
        if (currentMessage != null) {
            for (BbMessageContent c : currentMessage) {
                if (BbSendMessageType.TEXT.equals(c.getType())) {
                    askText.append(c.getData() == null ? "" : c.getData().toString());
                } else if (BbSendMessageType.NET_IMAGE.equals(c.getType())) {
                    parts.add(MessageContent.netImage(String.valueOf(c.getData())));
                } else if (BbSendMessageType.LOCAL_IMAGE.equals(c.getType())) {
                    parts.add(MessageContent.base64Image(String.valueOf(c.getData())));
                }
            }
        }
        parts.add(MessageContent.text(askText.toString()));
        return ChatMessage.user(parts);
    }

    /** 过滤掉与本轮消息文本重复的历史项（避免重复发给模型）。 */
    private static List<ChatHistory> filterDuplicateOfCurrent(
            List<ChatHistory> history, List<BbMessageContent> currentMessage) {
        if (currentMessage == null) {
            return history;
        }
        String currentJson = JSON.toJSONString(currentMessage);
        return history.stream()
                .filter(h -> h.getText() == null || !currentJson.contains(h.getText()))
                .collect(Collectors.toList());
    }

    private static boolean keepForText(BbMessageContent content) {
        String type = content.getType();
        return !BbSendMessageType.LOCAL_IMAGE.equals(type)
                && !BbSendMessageType.REPLY.equals(type)
                && !BbSendMessageType.NET_IMAGE.equals(type);
    }

    private static String stringifyPart(BbMessageContent content) {
        if (content.getData() == null) {
            return "";
        }
        if (BbSendMessageType.AT.equals(content.getType())) {
            return "@" + content.getData();
        }
        return content.getData().toString();
    }

    /** 仅供构建测试 fixture 时使用：包装单条文本为 {@link ChatMessage} 列表。 */
    public static List<ChatMessage> singleUser(String text) {
        return Collections.singletonList(ChatMessage.user(text));
    }
}
