package com.bb.bot.entity.qq;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

/**
 * 上传富媒体请求实体
 * @author ren
 */
@Data
public class UploadMediaRequest {

    /**
     * 媒体类型：1 图片，2 视频，3 语音，4 文件（暂不开放）
     * 资源格式要求
     * 图片：png/jpg，视频：mp4，语音：silk
     */
    @JSONField(name = "file_type")
    private Integer fileType;

    private String url;

    @JSONField(name = "srv_send_msg")
    private Boolean srvSendMsg = false;

    /**
     * 生成图片上传请求
     *
     * @param url 图片url
     * @return 封装后的请求实体
     */
    public static UploadMediaRequest buildImageRequest(String url) {
        UploadMediaRequest request = new UploadMediaRequest();
        request.setFileType(1);
        request.setUrl(url);
        return request;
    }
}
