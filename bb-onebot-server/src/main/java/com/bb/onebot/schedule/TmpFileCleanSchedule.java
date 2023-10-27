package com.bb.onebot.schedule;

import com.bb.onebot.util.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * @Description 定时清理临时文件
 * @Author ren
 * @Date 2023/10/27 10:01
 */
@EnableScheduling
@Component
@Slf4j
public class TmpFileCleanSchedule {

    /**
     * 每60分钟清理一次临时文件
     */
    @Scheduled(cron = "0 0/60 * * * *")
    public void cleanTmpFile(){
        File file = new File(FileUtils.getAbsolutePath("tmp/"));
        log.info("开始清理临时文件，路径：" + file.getAbsolutePath());
        FileUtils.deleteAllFileFromFolder(file);

        log.info("临时文件清理完毕");
    }
}
