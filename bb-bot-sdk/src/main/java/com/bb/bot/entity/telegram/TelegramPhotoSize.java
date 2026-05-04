package com.bb.bot.entity.telegram;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

/**
 * telegram图片对象
 */
@Data
public class TelegramPhotoSize {

    @JSONField(name = "file_id")
    private String fileId;

    @JSONField(name = "file_unique_id")
    private String fileUniqueId;

    private Integer width;

    private Integer height;

    @JSONField(name = "file_size")
    private Long fileSize;
}
