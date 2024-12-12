package com.bb.bot.common.util.fileClient;

import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 * 文件服务器相关接口
 */
public interface FileClientApi {
    /**
     * 上传文件至临时目录并返回链接
     */
    String uploadTmpFile(InputStream inputStream);

    /**
     * 上传文件并返回链接
     */
    String uploadFile(InputStream inputStream, String remotePath);

    /**
     * 下载文件
     */
    InputStream downloadFile(String remotePath) throws FileNotFoundException;

    /**
     * 删除文件
     */
    void deleteFile(String remotePath);

    /**
     * 删除临时文件
     */
    void deleteTmpFile();
}
