package com.bb.bot.database.news.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bb.bot.database.news.entity.NewsRunStatsPo;
import com.bb.bot.database.news.mapper.NewsRunStatsMapper;
import com.bb.bot.database.news.service.INewsRunStatsService;
import org.springframework.stereotype.Service;

/**
 * 日报运行指标历史 Service 业务层处理。
 *
 * @author rym
 */
@Service
public class NewsRunStatsServiceImpl extends ServiceImpl<NewsRunStatsMapper, NewsRunStatsPo>
        implements INewsRunStatsService {

}
