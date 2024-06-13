package com.bb.bot.database.aiKeywordAndClue.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bb.bot.database.aiKeywordAndClue.entity.AiClue;
import com.bb.bot.database.aiKeywordAndClue.vo.ClueDetail;

import java.util.List;

/**
 * <p>
 * ai线索表 服务类
 * </p>
 *
 * @author misu
 * @since 2024-06-12
 */
public interface IAiClueService extends IService<AiClue> {

    /**
     * 根据内容查询线索
     */
    public List<String> selectClue(String content);

    /**
     * 获取线索详情
     */
    public List<ClueDetail> getClueDetailList();

    /**
     * 导入群组线索
     */
    public void importGroupClue(String groupId, List<ClueDetail> clueDetailList);

    /**
     * 删除线索
     */
    public boolean deleteClue(Long clueId);
}
