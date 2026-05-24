package com.bb.bot.constant;

/**
 * BB 客户端在认证握手（{@code BbAuthMessage.capabilities}）时上报的能力位。
 *
 * <p>服务端据此降级：客户端不报某能力时，回退到老协议行为，老客户端零改造仍可用。</p>
 *
 * @author ren
 */
public class BbCapability {

    /** 支持 streamId/streamState 流式帧的 edit-in-place 呈现；不报则回退分段连发。 */
    public static final String STREAM = "stream";

    /** 支持 localFile/netFile 附件消息；不报则文件消息应避免下发。 */
    public static final String FILE = "file";

    private BbCapability() {}
}
