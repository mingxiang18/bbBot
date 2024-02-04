package com.bb.bot.database.splatoon.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 斯普拉遁3打工敌人详情对象 splatoon_coop_enemy_detail
 *
 * @author rym
 * @date 2024-02-01
 */
@Data
@ApiModel("斯普拉遁3打工敌人详情")
@TableName(value = "splatoon_coop_enemy_detail")
public class SplatoonCoopEnemyDetail
{
    private static final long serialVersionUID = 1L;

    @ApiModelProperty("主键")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty("打工记录id")
    private String coopId;

    @ApiModelProperty("敌人id")
    private String enemyId;

    @ApiModelProperty("敌人名称")
    private String enemyName;

    @ApiModelProperty("击杀数量")
    private Integer defeatCount;

    @ApiModelProperty("团队击杀数量")
    private Integer teamDefeatCount;

    @ApiModelProperty("出现数量")
    private Integer popCount;

}
