package com.bb.bot.entity.telegram;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

/**
 * telegram文件对象
 */
@Data
public class TelegramFile {

    @JSONField(name = "file_id")
    private String fileId;

    @JSONField(name = "file_unique_id")
    private String fileUniqueId;

    @JSONField(name = "file_size")
    private Long fileSize;

    @JSONField(name = "file_path")
    private String filePath;
}
