package com.bb.bot.database.news.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bb.bot.database.news.entity.NewsDailyPo;
import com.bb.bot.database.news.mapper.NewsDailyMapper;
import com.bb.bot.database.news.service.INewsDailyService;
import org.springframework.stereotype.Service;

/**
 * 每日资讯日报元信息 Service 业务层处理。
 *
 * @author rym
 * @since 2026-05-30
 */
@Service
public class NewsDailyServiceImpl extends ServiceImpl<NewsDailyMapper, NewsDailyPo> implements INewsDailyService {

}
