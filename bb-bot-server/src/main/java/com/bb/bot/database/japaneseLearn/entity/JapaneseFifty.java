package com.bb.bot.database.japaneseLearn.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 日语五十音表对象 japanese_fifty
 *
 * @author rym
 * @date 2023-10-31
 */
@Data
@ApiModel("日语五十音表")
@TableName(value = "japanese_fifty")
public class JapaneseFifty
{
    private static final long serialVersionUID = 1L;

    @ApiModelProperty("主键")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    @ApiModelProperty("片假名")
    private String hiragana;

    @ApiModelProperty("平假名")
    private String katakana;

    @ApiModelProperty("音标")
    private String phonetic;

    @ApiModelProperty("提示")
    private String tips;

}
