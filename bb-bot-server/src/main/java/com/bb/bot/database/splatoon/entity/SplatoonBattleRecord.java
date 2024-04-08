package com.bb.bot.database.splatoon.entity;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 斯普拉遁3对战记录对象 splatoon_battle_record
 *
 * @author rym
 * @date 2024-04-02
 */
@Data
@ApiModel("斯普拉遁3对战记录")
@TableName(value = "splatoon_battle_record")
public class SplatoonBattleRecord {
    private static final long serialVersionUID = 1L;

    @ApiModelProperty("自增id")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty("对战记录id")
    private String appBattleId;

    @ApiModelProperty("用户账户id")
    private String userId;

    @ApiModelProperty("对战模式id")
    private String vsModeId;

    @ApiModelProperty("对战模式名称")
    private String vsModeName;

    @ApiModelProperty("对战子模式，仅蛮颓比赛有，open-开放，其他码值是挑战")
    private String vsSubMode;

    @ApiModelProperty("对战规则id")
    private String vsRuleId;

    @ApiModelProperty("对战规则名称")
    private String vsRuleName;

    @ApiModelProperty("对战地图id")
    private String vsStageId;

    @ApiModelProperty("对战地图名称")
    private String vsStageName;

    @ApiModelProperty("对战结果")
    private String judgement;

    @ApiModelProperty("对战计数")
    private Integer score;

    @ApiModelProperty("段位")
    private String rankCode;

    @ApiModelProperty("分数变动")
    private Integer pointChange;

    @ApiModelProperty("技术点数")
    private Integer power;

    @ApiModelProperty("游玩时间")
    private LocalDateTime playedTime;

}
