package com.bb.bot.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.database.userConfigInfo.entity.UserConfigValue;
import com.bb.bot.database.userConfigInfo.service.IUserConfigValueService;
import com.bb.bot.handler.splatoon.BbSplatoonUserHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 斯普拉遁对战记录自动上传定时任务
 * @author ren
 */
@Slf4j
@Component
@EnableScheduling
public class SplatoonRecordsUploadSchedule {

    @Autowired
    private IUserConfigValueService userConfigValueService;

    @Autowired
    private BbSplatoonUserHandler bbSplatoonUserHandler;

    /**
     * 每4小时进行一次记录上传
     */
    @Scheduled(cron = "${splaoon.recordsUpload.cron:0 0 0/4 * * *}")
    public void recordsUpload() {
        List<UserConfigValue> list = userConfigValueService.list(new LambdaQueryWrapper<UserConfigValue>()
                .eq(UserConfigValue::getType, "NSO")
                .eq(UserConfigValue::getKeyName, "autoUploadRecords")
                .eq(UserConfigValue::getValueName, "1"));

        for (UserConfigValue userConfigValue : list) {
            //获取配置的用户id
            String userId = userConfigValue.getUserId();

            log.info("开始上传用户：{}，的斯普拉遁记录", userId);

            try {
                //开始上传记录
                bbSplatoonUserHandler.syncCoopRecords(userId);
                bbSplatoonUserHandler.syncBattleRecords(userId);
            }catch (Exception e) {
                log.error("用户：【" + userId + "】的斯普拉遁记录上传失败", e);
            }
        }
    }
}
