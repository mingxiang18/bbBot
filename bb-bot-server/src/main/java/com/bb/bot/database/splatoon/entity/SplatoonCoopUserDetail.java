package com.bb.bot.database.splatoon.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 斯普拉遁3玩家打工详情对象 splatoon_coop_user_detail
 *
 * @author rym
 * @since 2024-02-01
 */
@Data
@ApiModel("斯普拉遁3玩家打工详情")
@TableName(value = "splatoon_coop_user_detail")
public class SplatoonCoopUserDetail
{
    private static final long serialVersionUID = 1L;

    @ApiModelProperty("主键")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty("打工记录id")
    private String coopId;

    @ApiModelProperty("是否是用户本人")
    private Boolean meFlag;

    @ApiModelProperty("玩家id")
    private String playerId;

    @ApiModelProperty("玩家名称")
    private String playerName;

    @ApiModelProperty("玩家名片上的标识码")
    private String playerCode;

    @ApiModelProperty("玩家名片上的称号")
    private String playerTag;

    @ApiModelProperty("玩家工服名称")
    private String playerClothesName;

    @ApiModelProperty("每波的武器名称，逗号分割")
    private String weapons;

    @ApiModelProperty("特殊武器id")
    private String specialWeaponId;

    @ApiModelProperty("特殊武器名称")
    private String specialWeaponName;

    @ApiModelProperty("击杀大鲑鱼数量")
    private Integer defeatEnemyCount;

    @ApiModelProperty("运送红蛋数量")
    private Integer deliverRedCount;

    @ApiModelProperty("运送金蛋数量")
    private Integer deliverGlodenCount;

    @ApiModelProperty("协助金蛋数量")
    private Integer assistGlodenCount;

    @ApiModelProperty("救人数")
    private Integer rescueCount;

    @ApiModelProperty("被拯救数")
    private Integer rescuedCount;

}
