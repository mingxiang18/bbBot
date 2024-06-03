package com.bb.bot.util.imageUpload;

import java.io.File;

/**
 * 图片上传相关接口
 */
public interface ImageUploadApi {
    /**
     * 上传文件并返回链接
     */
    String uploadImage(File file);

    /**
     * 删除所有上传文件
     */
    void deleteAllImage();
}
