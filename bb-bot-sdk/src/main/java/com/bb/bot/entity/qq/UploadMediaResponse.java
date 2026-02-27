package com.bb.bot.entity.qq;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

/**
 * 上传富媒体响应实体
 * @author ren
 */
@Data
public class UploadMediaResponse {

    @JSONField(name = "file_uuid")
    private String fileUuid;

    /**
     * 文件信息，用于发消息接口的 media 字段使用
     */
    @JSONField(name = "file_info")
    private String fileInfo;

    /**
     * 有效期，表示剩余多少秒到期，到期后 file_info 失效，当等于 0 时，表示可长期使用
     */
    private int ttl;

    /**
     * 发送消息的唯一ID，当srv_send_msg设置为true时返回
     */
    private String id;
}
