package com.bb.bot.schedule;

import com.bb.bot.common.util.ImageUploadClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * @Description 定时清理上传到图床的文件
 * @Author ren
 * @Date 2023/10/27 10:01
 */
@EnableScheduling
@Component
@Slf4j
public class TmpImageCleanSchedule {

    @Autowired
    private ImageUploadClient imageUploadClient;

    /**
     * 每天23点30清理一次临时图片
     * 图片仅是临时保存，用于中转给qq接收，定时清理图片，防止图床占用满了
     */
    @Scheduled(cron = "0 30 23 * * *")
    public void cleanTmpImage(){
        imageUploadClient.deleteAllImage();
    }
}
