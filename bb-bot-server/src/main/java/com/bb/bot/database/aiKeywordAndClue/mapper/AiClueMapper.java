package com.bb.bot.database.aiKeywordAndClue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bb.bot.database.aiKeywordAndClue.entity.AiClue;
import com.bb.bot.database.aiKeywordAndClue.vo.ClueDetailVo;

import java.util.List;

/**
 * <p>
 * ai线索表 Mapper 接口
 * </p>
 *
 * @author misu
 * @since 2024-06-12
 */
public interface AiClueMapper extends BaseMapper<AiClue> {

    List<String> selectClue(String content);

    List<ClueDetailVo> selectClueDetail();

    boolean deleteClue(Long clueId);
}
