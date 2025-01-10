package com.bb.bot.database.japaneseLearn.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bb.bot.database.japaneseLearn.entity.JapaneseFifty;
import com.bb.bot.database.japaneseLearn.mapper.JapaneseFiftyMapper;
import com.bb.bot.database.japaneseLearn.service.IJapaneseFiftyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 日语五十音Service业务层处理
 *
 * @author rym
 * @since 2023-10-31
 */
@Service
public class JapaneseFiftyServiceImpl extends ServiceImpl<JapaneseFiftyMapper, JapaneseFifty> implements IJapaneseFiftyService {
    @Autowired
    private JapaneseFiftyMapper japaneseFiftyMapper;


}
