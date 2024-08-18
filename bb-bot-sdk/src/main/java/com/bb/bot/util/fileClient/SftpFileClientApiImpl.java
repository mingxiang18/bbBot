package com.bb.bot.util.fileClient;

import com.bb.bot.config.FilePathConfig;
import com.bb.bot.util.FileUtils;
import com.jcraft.jsch.*;
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

    @Value("${fileClient.sftp.host}")
    private String host;
    @Value("${fileClient.sftp.port}")
    private int port;
    @Value("${fileClient.sftp.username}")
    private String username;
    @Value("${fileClient.sftp.password}")
    private String password;
    @Value("${fileClient.sftp.remoteDir}")
    private String remoteDir;
    /**
     * 需要提供一个可以公网访问到指定文件目录的地址，通过nginx代理也好自己web服务器代理也好，否则uploadFile返回的地址无法访问
     */
    @Value("${fileClient.sftp.remoteReadAddress:http://127.0.0.1:80}")
    private String remoteReadAddress;

    @Autowired
    private FilePathConfig filePathConfig;

    private ChannelSftp sftpChannel;

    /**
     * 初始化连接
     */
    @PostConstruct
    public void reconnect() throws JSchException {
        disconnect();

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
        if (sftpChannel != null) {
            if (sftpChannel.getSession() != null && sftpChannel.getSession().isConnected()) {
                sftpChannel.getSession().disconnect();
            }
            if (sftpChannel.isConnected()) {
                sftpChannel.disconnect();
            }
        }
    }

    /**
     * 判断连接状态，如果没有连接则尝试连接
     */
    @SneakyThrows
    private void judgeConnectStatus() {
        reconnect();
//        if (!sftpChannel.getSession().isConnected()) {
//            reconnect();
//        }
//        if (!sftpChannel.isConnected()) {
//            reconnect();
//        }
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
        return remoteReadAddress + remotePath;
    }

    @Override
    public InputStream downloadFile(String remotePath) throws FileNotFoundException {
        judgeConnectStatus();

        //从远程服务器获取文件流
        try {
            return sftpChannel.get(remoteDir + remotePath);
        }catch (SftpException e) {
            //文件不存在时特殊处理
            if (ChannelSftp.SSH_FX_NO_SUCH_FILE == e.id) {
                throw new FileNotFoundException();
            }
            throw new RuntimeException(e);
        }catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @SneakyThrows
    public void deleteFile(String remotePath) {
        judgeConnectStatus();

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
