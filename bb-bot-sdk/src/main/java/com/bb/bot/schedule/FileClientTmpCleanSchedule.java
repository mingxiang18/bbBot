package com.bb.bot.schedule;

import com.bb.bot.util.imageUpload.FileClientApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * @Description 定时清理文件服务器的临时文件
 * @Author ren
 * @Date 2023/10/27 10:01
 */
@EnableScheduling
@Component
@Slf4j
public class FileClientTmpCleanSchedule {

    @Autowired
    private FileClientApi fileClientApi;

    /**
     * 每天23点30清理一次临时图片
     * 图片仅是临时保存，用于中转给qq接收，定时清理图片
     */
    @Scheduled(cron = "0 30 23 * * *")
    public void cleanTmpFile(){
        log.info("开始清理文件服务器的临时文件");
        fileClientApi.deleteTmpFile();
        log.info("文件服务器的临时文件清理完成");
    }
}
