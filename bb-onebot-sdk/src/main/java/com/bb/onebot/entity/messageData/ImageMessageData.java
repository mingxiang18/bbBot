package com.bb.onebot.entity.messageData;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图片消息类型
 * @author ren
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class ImageMessageData {
    /**
     * 图片路径
     */
    private String file;

    /**
     *     type	✓	✓	flash	图片类型，flash 表示闪照，无此参数表示普通图片
     *     url	✓		-	图片 URL
     *     cache		✓	0 1	只在通过网络 URL 发送时有效，表示是否使用已缓存的文件，默认 1
     *     proxy		✓	0 1	只在通过网络 URL 发送时有效，表示是否通过代理下载文件（需通过环境变量或配置文件配置代理），默认 1
     *     timeout		✓	-	只在通过网络 URL 发送时有效，单位秒，表示下载网络文件的超时时间，默认不超时
     */
}
