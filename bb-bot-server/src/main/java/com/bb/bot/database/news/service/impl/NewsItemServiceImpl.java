package com.bb.bot.database.news.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bb.bot.database.news.entity.NewsItemPo;
import com.bb.bot.database.news.mapper.NewsItemMapper;
import com.bb.bot.database.news.service.INewsItemService;
import org.springframework.stereotype.Service;

/**
 * 每日资讯条目 Service 业务层处理。
 *
 * @author rym
 * @since 2026-05-30
 */
@Service
public class NewsItemServiceImpl extends ServiceImpl<NewsItemMapper, NewsItemPo> implements INewsItemService {

}
