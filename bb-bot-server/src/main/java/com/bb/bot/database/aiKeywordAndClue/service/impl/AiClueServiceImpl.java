package com.bb.bot.database.aiKeywordAndClue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bb.bot.database.aiKeywordAndClue.entity.AiClue;
import com.bb.bot.database.aiKeywordAndClue.entity.AiKeyword;
import com.bb.bot.database.aiKeywordAndClue.entity.AiKeywordClue;
import com.bb.bot.database.aiKeywordAndClue.mapper.AiClueMapper;
import com.bb.bot.database.aiKeywordAndClue.service.IAiClueService;
import com.bb.bot.database.aiKeywordAndClue.service.IAiKeywordClueService;
import com.bb.bot.database.aiKeywordAndClue.service.IAiKeywordService;
import com.bb.bot.database.aiKeywordAndClue.vo.ClueDetail;
import com.bb.bot.database.aiKeywordAndClue.vo.ClueDetailVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * ai线索表 服务实现类
 * </p>
 *
 * @author misu
 * @since 2024-06-12
 */
@Service
public class AiClueServiceImpl extends ServiceImpl<AiClueMapper, AiClue> implements IAiClueService {

    @Autowired
    private IAiKeywordService aiKeywordService;

    @Autowired
    private IAiKeywordClueService aiKeywordClueService;
    @Autowired
    private AiClueMapper aiClueMapper;

    @Override
    public List<String> selectClue(String content) {
        return this.baseMapper.selectClue(content);
    }

    @Override
    public List<ClueDetail> getClueDetailList() {
        List<ClueDetailVo> clueDetailVoList = this.baseMapper.selectClueDetail();

        return clueDetailVoList.stream().map(clueDetailVo -> {
            ClueDetail clueDetail = new ClueDetail();
            clueDetail.setKeyword(Arrays.stream(clueDetailVo.getKeywords().split(",")).toList());
            clueDetail.setContent(clueDetailVo.getClueContent());
            clueDetail.setWeight(clueDetailVo.getWeight());
            return clueDetail;
        }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void importGroupClue(String groupId, List<ClueDetail> clueDetailList) {
        List<AiClue> saveClueList = new ArrayList<>();
        List<AiKeyword> saveKeywordList = new ArrayList<>();
        List<AiKeywordClue> saveKeywordClueList = new ArrayList<>();

        //关键字列表
        List<String> keywordList = new ArrayList<>();
        //添加所有线索的关键字到关键字列表
        clueDetailList.forEach(clueDetail -> keywordList.addAll(clueDetail.getKeyword()));
        //查询关键字列表, 封装为map（key-关键字，value-主键id）
        Map<String, Long> keywordMap = aiKeywordService.list(new LambdaQueryWrapper<AiKeyword>()
                .select(AiKeyword::getId, AiKeyword::getKeyName)
                .in(AiKeyword::getKeyName, keywordList))
                .stream()
                .collect(Collectors.toMap(AiKeyword::getKeyName, AiKeyword::getId));

        for (ClueDetail clueDetail : clueDetailList) {
            for (String keyword : clueDetail.getKeyword()) {
                //如果不存在关键字，新增关键字实体保存
                if (!keywordMap.containsKey(keyword)) {
                    //封装
                    AiKeyword aiKeyword = new AiKeyword();
                    aiKeyword.setId(IdWorker.getId());
                    aiKeyword.setGroupId(groupId);
                    aiKeyword.setKeyName(keyword);
                    saveKeywordList.add(aiKeyword);
                    //添加关键字到map
                    keywordMap.put(keyword, aiKeyword.getId());
                }
            }

            //封装线索
            AiClue aiClue = new AiClue();
            aiClue.setId(IdWorker.getId());
            aiClue.setClueContent(clueDetail.getContent());
            aiClue.setWeight(clueDetail.getWeight());
            saveClueList.add(aiClue);

            //封装关键字和线索关联
            for (String keyword : clueDetail.getKeyword()) {
                AiKeywordClue aiKeywordClue = new AiKeywordClue();
                aiKeywordClue.setKeywordId(keywordMap.get(keyword));
                aiKeywordClue.setClueId(aiClue.getId());
                saveKeywordClueList.add(aiKeywordClue);
            }
        }

        //批量添加
        saveBatch(saveClueList);
        aiKeywordService.saveBatch(saveKeywordList);
        aiKeywordClueService.saveBatch(saveKeywordClueList);
    }

    @Override
    public boolean deleteClue(Long clueId) {
        return aiClueMapper.deleteClue(clueId);
    }
}
