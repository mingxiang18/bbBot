package com.bb.bot.database.splatoon.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 斯普拉遁3用户对战详情对象 splatoon_battle_user_detail
 *
 * @author rym
 * @date 2024-04-02
 */
@Data
@ApiModel("斯普拉遁3用户对战详情")
@TableName(value = "splatoon_battle_user_detail")
public class SplatoonBattleUserDetail {
    private static final long serialVersionUID = 1L;

    @ApiModelProperty("自增id")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty("对战记录id")
    private String battleId;

    @ApiModelProperty("是否是用户本人")
    private Integer meFlag;

    @ApiModelProperty("队伍标识，1-我方队伍，2-敌方队伍")
    private Integer teamFlag;

    @ApiModelProperty("队伍编号")
    private Integer teamOrder;

    @ApiModelProperty("玩家id")
    private String playerId;

    @ApiModelProperty("玩家名称")
    private String playerName;

    @ApiModelProperty("玩家名片上的标识码")
    private String playerCode;

    @ApiModelProperty("玩家名片上的称号")
    private String playerTag;

    @ApiModelProperty("玩家名片上的徽章id，英文逗号分割多个id")
    private String playerBadges;

    @ApiModelProperty("玩家名片上的背景id")
    private String playerBackground;

    @ApiModelProperty("玩家头部装备")
    private String playerHeadGear;

    @ApiModelProperty("玩家衣服装备")
    private String playerClothesGear;

    @ApiModelProperty("玩家鞋子装备")
    private String playerShoesGear;

    @ApiModelProperty("武器id")
    private String weaponId;

    @ApiModelProperty("武器名称")
    private String weaponName;

    @ApiModelProperty("特殊武器id")
    private String weaponSpecialId;

    @ApiModelProperty("特殊武器名称")
    private String weaponSpecialName;

    @ApiModelProperty("副武器id")
    private String weaponSubWeaponId;

    @ApiModelProperty("副武器名称")
    private String weaponSubWeaponName;

    @ApiModelProperty("涂地数")
    private Integer paintCount;

    @ApiModelProperty("击杀数（击杀+助攻）")
    private Integer killCount;

    @ApiModelProperty("死亡数")
    private Integer deathCount;

    @ApiModelProperty("协助击杀数")
    private Integer assistCount;

    @ApiModelProperty("特殊武器使用数")
    private Integer specialCount;

}
