package com.bb.bot.database.splatoon.entity;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 斯普拉遁3打工记录对象 splatoon_coop_records
 *
 * @author rym
 * @date 2024-02-01
 */
@Data
@ApiModel("斯普拉遁3打工记录")
@TableName(value = "splatoon_coop_records")
public class SplatoonCoopRecord
{
    private static final long serialVersionUID = 1L;

    @ApiModelProperty("自增id")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty("打工记录id")
    private String appCoopId;

    @ApiModelProperty("用户账户id")
    private String userId;

    @ApiModelProperty("规则")
    private String rule;

    @ApiModelProperty("游玩时间")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDateTime playedTime;

    @ApiModelProperty("打工地图id")
    private String coopStageId;

    @ApiModelProperty("打工地图名称")
    private String coopStageName;

    @ApiModelProperty("危险度")
    private String dangerRate;

    @ApiModelProperty("结束后段位id")
    private String afterGradeId;

    @ApiModelProperty("结束后段位名称")
    private String afterGradeName;

    @ApiModelProperty("结束后段位点数")
    private Integer afterGradePoint;

    @ApiModelProperty("分数变化标识")
    private String gradePointDiff;

    @ApiModelProperty("结束波数，0-通过，其余数字代表具体波数")
    private Integer resultWave;

    @ApiModelProperty("团队金蛋运蛋数")
    private Integer teamGlodenCount;

    @ApiModelProperty("团队红蛋运蛋数")
    private Integer teamRedCount;

    @ApiModelProperty("boss的id")
    private String bossId;

    @ApiModelProperty("boss名称")
    private String bossName;

    @ApiModelProperty("boss是否成功讨伐")
    private Boolean bossDefeatFlag;

    @ApiModelProperty("获得金鳞片数")
    private Integer goldScale;

    @ApiModelProperty("获得银鳞片数")
    private Integer silverScale;

    @ApiModelProperty("获得铜鳞片数")
    private Integer bronzeScale;

    @ApiModelProperty("武器1")
    private String weapon1;

    @ApiModelProperty("武器2")
    private String weapon2;

    @ApiModelProperty("武器3")
    private String weapon3;

    @ApiModelProperty("武器4")
    private String weapon4;

}
