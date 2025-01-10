package com.bb.bot.database.splatoon.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 斯普拉遁3打工波数详情对象 splatoon_coop_wave_detail
 *
 * @author rym
 * @since 2024-02-01
 */
@Data
@ApiModel("斯普拉遁3打工波数详情")
@TableName(value = "splatoon_coop_wave_detail")
public class SplatoonCoopWaveDetail
{
    private static final long serialVersionUID = 1L;

    @ApiModelProperty("主键")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty("打工记录id")
    private String coopId;

    @ApiModelProperty("波次")
    private Integer waveNumber;

    @ApiModelProperty("潮水等级")
    private Integer waterLevel;

    @ApiModelProperty("波数事件id")
    private String eventWaveId;

    @ApiModelProperty("波数事件名称")
    private String eventWaveName;

    @ApiModelProperty("目标运送蛋数")
    private Integer deliverNorm;

    @ApiModelProperty("金蛋出现数量")
    private Integer goldenPopCount;

    @ApiModelProperty("团队运送金蛋数量")
    private Integer teamDeliverCount;

    @ApiModelProperty("使用的特殊武器ids")
    private String specialWeaponIds;

    @ApiModelProperty("使用的特殊武器名称")
    private String specialWeaponNames;

}
