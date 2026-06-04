package com.bb.bot.database.news.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bb.bot.database.news.entity.NewsSourceHealthPo;
import com.bb.bot.database.news.mapper.NewsSourceHealthMapper;
import com.bb.bot.database.news.service.INewsSourceHealthService;
import org.springframework.stereotype.Service;

/**
 * 资讯源健康记录 Service 业务层处理。
 *
 * @author rym
 */
@Service
public class NewsSourceHealthServiceImpl extends ServiceImpl<NewsSourceHealthMapper, NewsSourceHealthPo>
        implements INewsSourceHealthService {

}
