package com.bb.bot.util.fileClient;

import com.bb.bot.config.FilePathConfig;
import com.bb.bot.config.ServerConfig;
import com.bb.bot.util.FileUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.*;

/**
 * 本地文件工具
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix="fileClient",name = "type", havingValue = "local", matchIfMissing = true)
public class LocalFileClientApiImpl implements FileClientApi {

    @Autowired
    private ServerConfig serverConfig;

    @Autowired
    private FilePathConfig filePathConfig;

    @Override
    @SneakyThrows
    public String uploadTmpFile(InputStream inputStream) {
        return uploadFile(inputStream, "tmp/" + System.currentTimeMillis() + ".png");
    }

    @Override
    @SneakyThrows
    public String uploadFile(InputStream inputStream, String remotePath) {
        File imageFile =  new File(filePathConfig.getFilePath() + remotePath);
        try (FileOutputStream outputStream = new FileOutputStream(imageFile)) {
            byte[] buf = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buf)) > 0) {
                outputStream.write(buf, 0, bytesRead);
            }
        }

        //返回可访问获取的网络链接
        return "http://" + serverConfig.getIp() + ":" + serverConfig.getPort() + "/img/getImage/" + imageFile.getName();
    }

    @Override
    @SneakyThrows
    public InputStream downloadFile(String remotePath) {
        return new FileInputStream(filePathConfig.getFilePath() + remotePath);
    }

    @Override
    public void deleteFile(String remotePath) {
        FileUtils.deleteFile(filePathConfig.getFilePath() + remotePath);
    }

    @Override
    public void deleteTmpFile() {
        FileUtils.deleteAllFileFromFolder(new File(filePathConfig.getFilePath() + "tmp"));
    }
}
