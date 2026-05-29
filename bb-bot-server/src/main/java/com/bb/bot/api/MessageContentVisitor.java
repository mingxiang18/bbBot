package com.bb.bot.api;

import com.bb.bot.constant.BbSendMessageType;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbSendMessage;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/**
 * 渠道消息内容遍历公共工具。
 *
 * <p>各渠道发送实现（QQ/Discord/OneBot 等）原本各自 {@code for} 循环 + {@code if/else if}
 * 判断 {@link BbMessageContent#getType()} 的逻辑高度同构，差异只在于每种内容类型的处理体
 * （QQ 走 media 上传、Discord 走 {@code <@id>} 拼接等）。这里把遍历与类型分派抽出来，
 * 渠道只需在回调里实现差异处理。</p>
 */
public final class MessageContentVisitor {

    private MessageContentVisitor() {
    }

    /**
     * 遍历消息内容列表，按类型分派到对应回调。
     *
     * <p>语义约定（与各渠道原实现保持一致）：</p>
     * <ul>
     *   <li>列表为空（null 或 size=0）：不触发任何回调，直接返回。</li>
     *   <li>{@link BbSendMessageType#TEXT}：以 {@code data.toString()} 调用 {@code onText}。</li>
     *   <li>{@link BbSendMessageType#AT}：以 {@code data.toString()} 调用 {@code onAt}。</li>
     *   <li>{@link BbSendMessageType#LOCAL_IMAGE}：以 {@code (File) data} 调用 {@code onLocalImage}。</li>
     *   <li>{@link BbSendMessageType#NET_IMAGE}：以 {@code data.toString()} 调用 {@code onNetImage}。</li>
     *   <li>其它类型（如 REPLY/LOCAL_FILE/NET_FILE）或未知类型：不触发任何回调。</li>
     *   <li>某类型 data 为 null：跳过该条，不触发回调（避免 NPE）。</li>
     *   <li>对应回调为 null：跳过该类型（渠道不关心该类型即可不传）。</li>
     * </ul>
     *
     * @param bbSendMessage 待发送消息
     * @param onText        文本内容回调，可空
     * @param onLocalImage  本地图片内容回调（参数为 {@link File}），可空
     * @param onAt          @用户内容回调（参数为被 @ 的用户标识），可空
     * @param onNetImage    网络图片内容回调（参数为图片 URL），可空
     */
    public static void forEachContent(BbSendMessage bbSendMessage,
                                      Consumer<String> onText,
                                      Consumer<File> onLocalImage,
                                      Consumer<String> onAt,
                                      Consumer<String> onNetImage) {
        if (bbSendMessage == null) {
            return;
        }
        List<BbMessageContent> messageList = bbSendMessage.getMessageList();
        if (messageList == null || messageList.isEmpty()) {
            return;
        }
        for (BbMessageContent content : messageList) {
            if (content == null) {
                continue;
            }
            String type = content.getType();
            Object data = content.getData();
            if (type == null || data == null) {
                continue;
            }
            if (BbSendMessageType.TEXT.equals(type)) {
                if (onText != null) {
                    onText.accept(data.toString());
                }
            } else if (BbSendMessageType.AT.equals(type)) {
                if (onAt != null) {
                    onAt.accept(data.toString());
                }
            } else if (BbSendMessageType.LOCAL_IMAGE.equals(type)) {
                if (onLocalImage != null && data instanceof File) {
                    onLocalImage.accept((File) data);
                }
            } else if (BbSendMessageType.NET_IMAGE.equals(type)) {
                if (onNetImage != null) {
                    onNetImage.accept(data.toString());
                }
            }
            // 其它类型（REPLY/LOCAL_FILE/NET_FILE/未知）不在本遍历职责内，由渠道自行处理。
        }
    }
}
