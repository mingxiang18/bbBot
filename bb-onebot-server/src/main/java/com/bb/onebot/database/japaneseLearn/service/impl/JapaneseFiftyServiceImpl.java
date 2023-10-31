package com.bb.onebot.database.japaneseLearn.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bb.onebot.database.japaneseLearn.entity.JapaneseFifty;
import com.bb.onebot.database.japaneseLearn.mapper.JapaneseFiftyMapper;
import com.bb.onebot.database.japaneseLearn.service.IJapaneseFiftyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 日语五十音Service业务层处理
 *
 * @author rym
 * @date 2023-10-31
 */
@Service
public class JapaneseFiftyServiceImpl extends ServiceImpl<JapaneseFiftyMapper, JapaneseFifty> implements IJapaneseFiftyService {
    @Autowired
    private JapaneseFiftyMapper japaneseFiftyMapper;


}
