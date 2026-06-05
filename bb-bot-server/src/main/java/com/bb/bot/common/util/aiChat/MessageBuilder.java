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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
        StringBuilder askText = new StringBuilder();

        // 图片一律以「ref + 链接」文本进入上下文（选择性识图）：不直接把图片 part 喂主模型，
        // 由 AI 据上下文决定是否调 analyze_image 看图。引用消息里的图同样给成 ref。
        if (replyTarget != null) {
            try {
                List<BbMessageContent> replyContents = JSON.parseObject(
                        replyTarget.getText(), new TypeReference<List<BbMessageContent>>() {});
                if (replyContents != null) {
                    for (BbMessageContent c : replyContents) {
                        if (BbSendMessageType.NET_IMAGE.equals(c.getType())) {
                            askText.append(imageRefNote(c));
                        }
                    }
                }
            } catch (Exception ignored) {
                // 引用消息解析失败不影响主流程
            }
        }

        if (currentMessage != null) {
            for (BbMessageContent c : currentMessage) {
                String type = c.getType();
                if (BbSendMessageType.TEXT.equals(type)) {
                    askText.append(c.getData() == null ? "" : c.getData().toString());
                } else if (BbSendMessageType.NET_IMAGE.equals(type)) {
                    askText.append(imageRefNote(c));
                } else if (BbSendMessageType.LOCAL_IMAGE.equals(type)) {
                    // 正常已被 InboundImageStore 规范化成 netImage；兜底给个无 ref 占位
                    askText.append("[图片]");
                } else if (BbSendMessageType.LOCAL_FILE.equals(type)
                        || BbSendMessageType.NET_FILE.equals(type)) {
                    // 文件附件无法直接喂模型，以文本提示告知；模型可用 file 工具处理
                    askText.append(fileNote(c));
                }
            }
        }

        List<MessageContent> parts = new ArrayList<>();
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
        // 保留 NET_IMAGE：入站已被 InboundImageStore 规范化成 netImage(ref)，历史里渲染成图片 ref 链接，
        // 让 AI 看到图、可用 analyze_image 自主分析。LOCAL_IMAGE（大 base64）与 REPLY 仍过滤。
        return !BbSendMessageType.LOCAL_IMAGE.equals(type)
                && !BbSendMessageType.REPLY.equals(type);
    }

    private static String stringifyPart(BbMessageContent content) {
        String type = content.getType();
        if (BbSendMessageType.LOCAL_FILE.equals(type) || BbSendMessageType.NET_FILE.equals(type)) {
            return fileNote(content);
        }
        if (BbSendMessageType.NET_IMAGE.equals(type)) {
            return imageRefNote(content);
        }
        if (content.getData() == null) {
            return "";
        }
        if (BbSendMessageType.AT.equals(type)) {
            return "@" + content.getData();
        }
        return content.getData().toString();
    }

    /**
     * 把图片（已规范化的 netImage，fileName=内容哈希、data=/img/&lt;hash&gt;.png）渲染成
     * 给模型可读的「图片 ref + 链接」文本提示。模型据此自主决定是否调 analyze_image 看图。
     */
    private static String imageRefNote(BbMessageContent content) {
        String url = content.getData() == null ? "" : content.getData().toString();
        String ref = StringUtils.isBlank(content.getFileName()) ? url : content.getFileName().trim();
        return "[图片 ref=" + ref + " 链接:" + url + "（想了解图片内容就用 analyze_image 工具分析这个 ref）]";
    }

    /**
     * 文件附件渲染成对模型可读的文本提示。
     *
     * <p>附件经 {@code AgentFileStore} 落盘后，{@code data} 会被改写成本地绝对路径，
     * 此时把路径一并告诉模型，模型可用 file_read 读取真实内容；否则（历史消息里
     * data 仍是旧 base64 / URL）只渲染文件名。</p>
     */
    private static String fileNote(BbMessageContent content) {
        String name = StringUtils.isBlank(content.getFileName()) ? "未命名文件" : content.getFileName();
        String localPath = localFilePath(content);
        if (localPath != null) {
            return "[附件文件：" + name + "，本地路径：" + localPath + "（用 file_read 读取内容）]";
        }
        return "[附件文件：" + name + "]";
    }

    /** 若 {@code data} 是一个已落盘的绝对文件路径则返回它，否则返回 null。 */
    private static String localFilePath(BbMessageContent content) {
        Object data = content.getData();
        if (data == null) {
            return null;
        }
        String s = data.toString();
        if (StringUtils.isBlank(s)) {
            return null;
        }
        try {
            Path p = Paths.get(s);
            if (p.isAbsolute() && Files.isRegularFile(p)) {
                return p.toString();
            }
        } catch (Exception ignored) {
            // data 不是合法路径（base64 / URL）→ 当作无路径
        }
        return null;
    }

    /** 仅供构建测试 fixture 时使用：包装单条文本为 {@link ChatMessage} 列表。 */
    public static List<ChatMessage> singleUser(String text) {
        return Collections.singletonList(ChatMessage.user(text));
    }
}
