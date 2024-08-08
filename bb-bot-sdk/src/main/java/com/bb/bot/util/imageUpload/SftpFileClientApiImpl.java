package com.bb.bot.util.imageUpload;

import com.bb.bot.config.FilePathConfig;
import com.bb.bot.config.ServerConfig;
import com.bb.bot.util.FileUtils;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.Properties;
import java.util.Vector;

/**
 * sftp图片上传工具
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix="fileClient",name = "type", havingValue = "sftp", matchIfMissing = false)
public class SftpFileClientApiImpl implements FileClientApi {

    @Value("${sftp.host}")
    private String host;
    @Value("${sftp.port}")
    private int port;
    @Value("${sftp.username}")
    private String username;
    @Value("${sftp.password}")
    private String password;
    @Value("${sftp.remoteDir}")
    private String remoteDir;

    @Autowired
    private FilePathConfig filePathConfig;

    @Value("${server.nginx.address:http://127.0.0.1:80}")
    private String serverNginxAddress;

    private ChannelSftp sftpChannel;

    /**
     * 初始化连接
     */
    @PostConstruct
    public void connect() throws JSchException {
        JSch jsch = new JSch();
        Session session = jsch.getSession(username, host, port);
        session.setPassword(password);

        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        session.connect();

        sftpChannel = (ChannelSftp) session.openChannel("sftp");
        sftpChannel.connect();
    }

    /**
     * 销毁bean前关闭连接
     */
    @PreDestroy
    public void disconnect() throws JSchException {
        if (sftpChannel != null && sftpChannel.isConnected()) {
            sftpChannel.disconnect();
        }
        if (sftpChannel.getSession() != null && sftpChannel.getSession().isConnected()) {
            sftpChannel.getSession().disconnect();
        }
    }

    /**
     * 判断连接状态，如果没有连接则尝试连接
     */
    @SneakyThrows
    private void judgeConnectStatus() {
        if (!sftpChannel.getSession().isConnected()) {
            sftpChannel.getSession().connect();
        }
        if (!sftpChannel.isConnected()) {
            sftpChannel.connect();
        }
    }

    @Override
    public String uploadTmpFile(InputStream inputStream) {
        return uploadFile(inputStream, "tmp/" + System.currentTimeMillis() + ".png");
    }

    @Override
    @SneakyThrows
    public String uploadFile(InputStream inputStream, String remotePath) {
        judgeConnectStatus();

        //上传到远程服务器
        sftpChannel.put(inputStream, remoteDir + remotePath);

        //返回远程服务器可访问的nginx地址
        return serverNginxAddress + remotePath;
    }

    @Override
    @SneakyThrows
    public InputStream downloadFile(String remotePath) {
        judgeConnectStatus();

        //从远程服务器获取文件流
        return sftpChannel.get(remoteDir + remotePath);
    }

    @Override
    @SneakyThrows
    public void deleteFile(String remotePath) {
        judgeConnectStatus();

        //删除本地目录下文件
        FileUtils.deleteFile(filePathConfig.getFilePath() + remotePath);
        //删除远程目录下文件
        sftpChannel.rm(remoteDir + remotePath);
    }

    @Override
    @SneakyThrows
    public void deleteTmpFile() {
        judgeConnectStatus();

        String remotePathDir = remoteDir + "tmp";
        // 获取目录下的所有文件
        Vector<ChannelSftp.LsEntry> fileList = sftpChannel.ls(remotePathDir);

        // 删除目录下的所有文件
        for (ChannelSftp.LsEntry entry : fileList) {
            String fileName = entry.getFilename();
            if (!fileName.equals(".") && !fileName.equals("..")) {
                String filePath = remotePathDir + "/" + fileName;
                sftpChannel.rm(filePath);
            }
        }
    }
}
