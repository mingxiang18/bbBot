package com.bb.bot.database.splatoon.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.bb.bot.database.splatoon.mapper.SplatoonBattleUserDetailMapper;
import com.bb.bot.database.splatoon.entity.SplatoonBattleUserDetail;
import com.bb.bot.database.splatoon.service.ISplatoonBattleUserDetailService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
 * 斯普拉遁3用户对战详情Service业务层处理
 *
 * @author rym
 * @date 2024-04-02
 */
@Service
public class SplatoonBattleUserDetailServiceImpl extends ServiceImpl<SplatoonBattleUserDetailMapper,SplatoonBattleUserDetail> implements ISplatoonBattleUserDetailService {
    @Autowired
    private SplatoonBattleUserDetailMapper splatoonBattleUserDetailMapper;


}
