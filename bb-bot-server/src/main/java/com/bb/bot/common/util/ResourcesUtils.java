package com.bb.bot.common.util;

import com.bb.bot.config.FilePathConfig;
import com.bb.bot.util.RestUtils;
import com.bb.bot.util.fileClient.FileClientApi;
import com.bb.bot.util.fileClient.LocalFileClientApiImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.*;

/**
 * 对于服务静态资源管理的工具
 *
 * 这个工具存在的主要原因是存在从网络中动态获取文件保存到静态资源的需要
 * bot服务是在容器中建立的，容器会漂移到不同的机器，容器消亡后保存的资源会丢失
 * 本来是可以通过持久卷挂载解决资源的问题，但是个人的云服务器带宽和内存都非常吃紧，资源通过网络读取非常慢，还是得优先从本地获取，遇到不存在得资源或需要上传资源时才和远程文件服务器交互
 */
@Slf4j
@Component
public class ResourcesUtils {

    @Autowired
    private FileClientApi fileClientApi;

    @Autowired
    private FilePathConfig filePathConfig;

    @Autowired
    private RestUtils restUtils;

    /**
     * 判断是否本地文件服务器
     */
    private boolean isLocalFileClient() {
        return fileClientApi instanceof LocalFileClientApiImpl;
    }

    /**
     * 请求指定目录下的静态资源
     * 如果不存在则调用webUrl获取资源，上传资源后返回File
     */
    public File getOrAddStaticResourceFromNet(String fileSubPath, String webUrl) {
        try {
            return getStaticResource(fileSubPath);
        }catch (FileNotFoundException e) {
            //如果静态资源不存在，发起网络请求获取并上传静态资源
            addStaticResourceFromNet(fileSubPath, webUrl);
            try {
                //重新获取静态资源
                return getStaticResource(fileSubPath);
            }catch (FileNotFoundException subE) {
                throw new RuntimeException(subE);
            }
        }
    }

    /**
     * 获取指定目录下的静态资源
     */
    public File getStaticResource(String fileSubPath) throws FileNotFoundException {
        //从本地获取
        File resourceFile = new File(filePathConfig.getFilePath() + fileSubPath);

        //如果文件不存在，尝试从文件服务器获取
        if (!resourceFile.exists()) {
            //如果文件服务器是本地，则直接跳过
            if (isLocalFileClient()) {
                throw new FileNotFoundException();
            }

            if (!resourceFile.getParentFile().exists()) {
                //建立目录
                resourceFile.getParentFile().mkdirs();
            }
            //将输入流写入文件
            try (InputStream inputStream = fileClientApi.downloadFile(fileSubPath);
                 FileOutputStream outputStream = new FileOutputStream(resourceFile);
            ) {
                byte[] buf = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buf)) > 0) {
                    outputStream.write(buf, 0, bytesRead);
                }
            }catch (FileNotFoundException e) {
                throw e;
            }catch (Exception e) {
                log.error("失败", e);
                throw new RuntimeException(e);
            }
        }
        //返回文件
        return resourceFile;
    }

    /**
     * 获取指定目录下的静态资源
     */
    public byte[] getStaticResourceToByte(String fileSubPath) throws FileNotFoundException {
        //从本地获取
        File resourceFile = getStaticResource(fileSubPath);

        //将输入流写入文件
        try (InputStream inputStream = new FileInputStream(resourceFile);
        ) {
            byte[] data = new byte[inputStream.available()];
            inputStream.read(data);
            return data;
        }catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 网络请求资源后添加指定目录下的静态资源
     */
    public void addStaticResourceFromNet(String fileSubPath, String webUrl) {
        try (InputStream inputStream = restUtils.getFileInputStream(webUrl);){
            addStaticResource(fileSubPath, inputStream);
        }catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 添加指定目录下的静态资源
     */
    public void addStaticResource(String fileSubPath, InputStream inputStream) {
        //从本地获取
        File resourceFile = new File(filePathConfig.getFilePath() + fileSubPath);

        //如果文件不存在
        if (!resourceFile.getParentFile().exists()) {
            //建立目录
            resourceFile.getParentFile().mkdirs();
        }

        //将输入流写入本地文件
        try (FileOutputStream outputStream = new FileOutputStream(resourceFile);) {
            byte[] buf = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buf)) > 0) {
                outputStream.write(buf, 0, bytesRead);
            }
        }catch (Exception e) {
            throw new RuntimeException(e);
        }

        //如果文件服务器是本地，则结束
        if (isLocalFileClient()) {
            return;
        }else {
            //否则上传到远程服务器
            try (FileInputStream fileInputStream = new FileInputStream(resourceFile);) {
                fileClientApi.uploadFile(fileInputStream, fileSubPath);
            }catch (Exception e) {
                throw new RuntimeException("静态资源【" + resourceFile.getAbsolutePath() + "】上传到远程文件服务器失败");
            }
        }
    }
}
