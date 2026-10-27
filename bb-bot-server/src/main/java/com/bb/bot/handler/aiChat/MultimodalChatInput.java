package com.bb.bot.handler.aiChat;

import com.alibaba.fastjson2.JSON;
import com.bb.bot.aiAgent.fs.InboundImageStore;
import com.bb.bot.common.util.aiChat.MessageBuilder;
import com.bb.bot.common.util.aiChat.provider.*;
import com.bb.bot.constant.BbSendMessageType;
import com.bb.bot.database.chatHistory.entity.ChatHistory;
import com.bb.bot.entity.bb.BbMessageContent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

/** 将当前/引用图片的真实字节与问题一起发送给视觉模型，不经过图片描述工具。 */
@Component
public class MultimodalChatInput {
    @Autowired private InboundImageStore imageStore;
    @Autowired private AiChatService aiChatService;
    @Value("${aiChat.directMultimodal.maxImages:4}") private int maxImages;
    private static final int MAX_BYTES = 20 * 1024 * 1024;
    private static final String LEGACY_HINT = "（想了解图片内容就用 analyze_image 工具分析这个 ref）";

    public List<MessageContent> prepare(AiChatTurnContext context, ModelTier tier) {
        ModelSpec currentModel = aiChatService.specForTier(tier);
        if (currentModel == null || !currentModel.isConfigured() || !currentModel.isVision()) return List.of();
        List<BbMessageContent> candidates = new ArrayList<>(context.currentContent());
        candidates.addAll(contents(context.getReplyTarget()));
        // 普通文字闲聊不带无关历史图；明确追问图片时补最近一条用户图片消息。
        if (!hasImages(candidates) && context.getReplyTarget() == null
                && context.getCurrentText() != null
                && context.getCurrentText().matches("(?s).*(图片|这张|那张|图里|图中|看图|识图|照片|截图).*")) {
            List<ChatHistory> history = context.historyList();
            for (int i = history.size() - 1; i >= Math.max(0, history.size() - 8); i--) {
                ChatHistory item = history.get(i);
                List<BbMessageContent> parts = contents(item);
                if (!MessageBuilder.BOT_USER_FLAG.equals(item.getUserQq()) && hasImages(parts)) {
                    candidates.addAll(parts);
                    break;
                }
            }
        }
        List<MessageContent> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int bytesUsed = 0, count = 0;
        for (BbMessageContent part : candidates) {
            if (!BbSendMessageType.NET_IMAGE.equals(part.getType())) continue;
            String ref = part.getData() == null ? part.getFileName() : part.getData().toString();
            String hash = imageStore.extractHash(ref);
            if (hash == null || !seen.add(hash)) continue;
            if (count >= Math.max(1, Math.min(maxImages, 4))) {
                result.add(MessageContent.text("[附件图片超出本轮上限，未读取其余图片]"));
                break;
            }
            byte[] bytes = imageStore.bytesForRef(hash);
            if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES - bytesUsed) {
                result.add(MessageContent.text("[图片 ref=" + hash + " 未能读取或超出大小限制，请勿推测内容]"));
                continue;
            }
            String mime = mime(bytes);
            if (mime == null) {
                result.add(MessageContent.text("[图片 ref=" + hash + " 格式不支持，未读取]"));
                continue;
            }
            result.add(MessageContent.text("[图片 ref=" + hash + "]"));
            result.add(MessageContent.base64Image("data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes)));
            bytesUsed += bytes.length;
            count++;
        }
        return count == 0 ? List.of() : result;
    }

    /** 保持历史角色与工具元数据；图片仅放在最后一条 USER 消息。 */
    public List<ChatMessage> attach(List<ChatMessage> messages, List<MessageContent> images) {
        if (images.isEmpty()) return messages;
        List<ChatMessage> result = new ArrayList<>();
        for (ChatMessage message : messages) {
            List<MessageContent> parts = new ArrayList<>();
            for (MessageContent part : message.getContents()) {
                parts.add(part.getType() == MessageContent.Type.TEXT && part.getValue() != null
                        ? MessageContent.text(part.getValue().replace(LEGACY_HINT, "")) : part);
            }
            ChatMessage copy = new ChatMessage(message.getRole(), parts);
            copy.setToolCalls(message.getToolCalls());
            copy.setToolCallId(message.getToolCallId());
            copy.setReasoningContent(message.getReasoningContent());
            result.add(copy);
        }
        ChatMessage last = result.get(result.size() - 1);
        if (last.getRole() != ChatMessage.Role.USER) throw new IllegalArgumentException("图片必须附在 USER 提问消息上");
        last.getContents().addAll(images);
        return result;
    }

    private List<BbMessageContent> contents(ChatHistory history) {
        if (history == null || history.getText() == null) return List.of();
        try {
            List<BbMessageContent> parts = JSON.parseArray(history.getText(), BbMessageContent.class);
            return parts == null ? List.of() : parts;
        } catch (Exception ignored) { return List.of(); }
    }
    private boolean hasImages(List<BbMessageContent> parts) {
        return parts.stream().anyMatch(p -> BbSendMessageType.NET_IMAGE.equals(p.getType()));
    }
    private String mime(byte[] b) {
        if (b.length >= 3 && (b[0] & 255) == 255 && (b[1] & 255) == 216 && (b[2] & 255) == 255) return "image/jpeg";
        if (b.length >= 8 && b[0] == (byte)137 && b[1] == 80 && b[2] == 78 && b[3] == 71) return "image/png";
        if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F') return "image/gif";
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        return null;
    }
}
