package com.bb.bot.util.imageUpload;

import com.bb.bot.config.ServerConfig;
import com.bb.bot.util.FileUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * 本地图片上传工具
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix="imageUpload",name = "type", havingValue = "local", matchIfMissing = true)
public class LocalImageUploadApiImpl implements ImageUploadApi{

    @Autowired
    private ServerConfig serverConfig;

    @Override
    @SneakyThrows
    public String uploadImage(File localFile) {
        File imageFile =  new File(FileUtils.getAbsolutePath("tmp/" + System.currentTimeMillis() + ".png"));
        //复制保存到tmp文件夹
        FileUtils.copyFileUsingFileStreams(localFile, imageFile);
        //返回可访问获取的网络链接
        return "http://" + serverConfig.getIp() + ":" + serverConfig.getPort() + "/img/getImage/" + imageFile.getName();
    }

    /**
     * 删除所有上传的图片
     */
    @Override
    public void deleteAllImage() {

    }
}
