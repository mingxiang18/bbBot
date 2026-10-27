package com.bb.bot.handler.aiChat;

import com.alibaba.fastjson2.JSON;
import com.bb.bot.aiAgent.fs.InboundImageStore;
import com.bb.bot.common.util.aiChat.MessageBuilder;
import com.bb.bot.common.util.aiChat.provider.AiChatService;
import com.bb.bot.common.util.aiChat.provider.ChatMessage;
import com.bb.bot.common.util.aiChat.provider.MessageContent;
import com.bb.bot.common.util.aiChat.provider.ModelSpec;
import com.bb.bot.common.util.aiChat.provider.ModelTier;
import com.bb.bot.constant.BbSendMessageType;
import com.bb.bot.database.chatHistory.entity.ChatHistory;
import com.bb.bot.entity.bb.BbMessageContent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 聊天图文输入组装器。
 *
 * <p>当前模型支持视觉时，从入站缓存读取原图，与当前问题一起发送。
 * 当前模型不支持视觉时不处理，保留原来的 analyze_image 工具和图片描述缓存流程。</p>
 */
@Component
public class MultimodalChatInput {

    /** 最近历史的扫描范围，避免追问时带入过久以前的图片。 */
    private static final int HISTORY_SCAN_LIMIT = 8;

    /** 同时限制图片数量和原始字节数，给 base64 膨胀及文字上下文留出请求空间。 */
    private static final int MAX_IMAGES = 4;

    private static final int MAX_IMAGE_BYTES = 20 * 1024 * 1024;

    private static final Pattern IMAGE_FOLLOW_UP =
            Pattern.compile("(?s).*(图片|这张|那张|图里|图中|看图|识图|照片|截图).*");

    /** 直传原图时移除旧工具提示，防止模型已经看到图片却再次调用分析工具。 */
    private static final String LEGACY_ANALYZE_HINT = "（想了解图片内容就用 analyze_image 工具分析这个 ref）";

    @Autowired

    private InboundImageStore imageStore;

    @Autowired

    private AiChatService aiChatService;

    @Value("${aiChat.directMultimodal.maxImages:4}")
    private int maxImages;

    /**
     * 为当前选中的模型准备图片内容，不改变模型路由。
     *
     * @return 图片及其说明片段；无可用图片时返回空列表，由调用方保留旧链路
     */
    public List<MessageContent> prepare(AiChatTurnContext context, ModelTier tier) {
        ModelSpec currentModel = aiChatService.specForTier(tier);
        if (currentModel == null || !currentModel.isConfigured() || !currentModel.isVision()) {
            return List.of();
        }

        List<BbMessageContent> candidates = selectImageCandidates(context);
        List<MessageContent> imageParts = new ArrayList<>();
        Set<String> seenHashes = new HashSet<>();
        int imageLimit = Math.max(1, Math.min(maxImages, MAX_IMAGES));
        int bytesUsed = 0;
        int imageCount = 0;

        for (BbMessageContent content : candidates) {
            if (!BbSendMessageType.NET_IMAGE.equals(content.getType())) {
                continue;
            }

            String imageRef =
                    content.getData() == null
                            ? content.getFileName()
                            : content.getData().toString();
            String imageHash = imageStore.extractHash(imageRef);
            if (imageHash == null || !seenHashes.add(imageHash)) {
                continue;
            }
            if (imageCount >= imageLimit) {
                imageParts.add(MessageContent.text("[附件图片超出本轮上限，未读取其余图片]"));
                break;
            }

            // 只读取已经规范化的本地缓存，不把临时 QQ CDN 地址交给模型下载。
            byte[] imageBytes = imageStore.bytesForRef(imageHash);
            if (imageBytes == null
                    || imageBytes.length == 0
                    || imageBytes.length > MAX_IMAGE_BYTES - bytesUsed) {
                imageParts.add(
                        MessageContent.text("[图片 ref=" + imageHash + " 未能读取或超出大小限制，请勿推测内容]"));
                continue;
            }

            String mimeType = detectMimeType(imageBytes);
            if (mimeType == null) {
                imageParts.add(MessageContent.text("[图片 ref=" + imageHash + " 格式不支持，未读取]"));
                continue;
            }

            String dataUrl =
                    "data:"
                            + mimeType
                            + ";base64,"
                            + Base64.getEncoder().encodeToString(imageBytes);
            imageParts.add(MessageContent.text("[图片 ref=" + imageHash + "]"));
            imageParts.add(MessageContent.base64Image(dataUrl));
            bytesUsed += imageBytes.length;
            imageCount++;
        }

        // 全部失败时不返回纯错误提示列表，避免调用方误判为已成功直传图片。
        return imageCount == 0 ? List.of() : imageParts;
    }

    /** 当前消息和明确引用优先；没有指定图片时，才为看图追问补充最近的用户图片。 */
    private List<BbMessageContent> selectImageCandidates(AiChatTurnContext context) {
        List<BbMessageContent> candidates = new ArrayList<>(context.currentContent());
        candidates.addAll(parseHistoryContents(context.getReplyTarget()));

        String currentText = context.getCurrentText();
        boolean needsHistoryImage =
                !hasImages(candidates)
                        && context.getReplyTarget() == null
                        && currentText != null
                        && IMAGE_FOLLOW_UP.matcher(currentText).matches();
        if (!needsHistoryImage) {
            return candidates;
        }

        List<ChatHistory> history = context.historyList();
        int firstIndex = Math.max(0, history.size() - HISTORY_SCAN_LIMIT);
        for (int index = history.size() - 1; index >= firstIndex; index--) {
            ChatHistory item = history.get(index);
            List<BbMessageContent> contents = parseHistoryContents(item);
            if (!MessageBuilder.BOT_USER_FLAG.equals(item.getUserQq()) && hasImages(contents)) {
                candidates.addAll(contents);
                break;
            }
        }
        return candidates;
    }

    /** 将原图附在最后一条 USER 提问中；历史角色、工具调用和推理字段保持完整。 使用副本去掉旧的工具提示，不修改后续还可能复用的原始上下文。 */
    public List<ChatMessage> attach(List<ChatMessage> messages, List<MessageContent> images) {
        if (images.isEmpty()) {
            return messages;
        }

        List<ChatMessage> result = new ArrayList<>(messages.size());
        for (ChatMessage message : messages) {
            result.add(copyWithoutAnalyzeHint(message));
        }

        ChatMessage question = result.get(result.size() - 1);
        if (question.getRole() != ChatMessage.Role.USER) {
            throw new IllegalArgumentException("图片必须附在 USER 提问消息上");
        }
        question.getContents().addAll(images);
        return result;
    }

    private ChatMessage copyWithoutAnalyzeHint(ChatMessage message) {
        List<MessageContent> contents = new ArrayList<>();
        for (MessageContent content : message.getContents()) {
            if (content.getType() == MessageContent.Type.TEXT && content.getValue() != null) {
                String text = content.getValue().replace(LEGACY_ANALYZE_HINT, "");
                contents.add(MessageContent.text(text));
            } else {
                contents.add(content);
            }
        }

        ChatMessage copy = new ChatMessage(message.getRole(), contents);
        copy.setToolCalls(message.getToolCalls());
        copy.setToolCallId(message.getToolCallId());
        copy.setReasoningContent(message.getReasoningContent());
        return copy;
    }

    /** 老历史可能是普通文本，解析失败时按没有图片处理。 */
    private List<BbMessageContent> parseHistoryContents(ChatHistory history) {
        if (history == null || history.getText() == null) {
            return List.of();
        }
        try {
            List<BbMessageContent> contents =
                    JSON.parseArray(history.getText(), BbMessageContent.class);
            return contents == null ? List.of() : contents;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private boolean hasImages(List<BbMessageContent> contents) {
        return contents.stream()
                .anyMatch(content -> BbSendMessageType.NET_IMAGE.equals(content.getType()));
    }

    /** 缓存文件统一使用 .png 后缀，因此根据真实文件头判断格式。 */
    private String detectMimeType(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (bytes.length >= 8
                && bytes[0] == (byte) 0x89
                && bytes[1] == 'P'
                && bytes[2] == 'N'
                && bytes[3] == 'G') {
            return "image/png";
        }
        if (bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
            return "image/gif";
        }
        if (bytes.length >= 12
                && bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F'
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
