package com.bb.bot.database.userConfigInfo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户相关数据对象 user_config_value
 *
 * @author rym
 * @date 2024-01-29
 */
@Data
@ApiModel("用户相关数据")
@TableName(value = "user_config_value")
public class UserConfigValue {
    private static final long serialVersionUID = 1L;

    @ApiModelProperty("主键")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty("用户id")
    private String userId;

    @ApiModelProperty("群聊或频道id")
    private String groupId;

    @ApiModelProperty("类型，用于特殊标识")
    private String type;

    @ApiModelProperty("key值")
    private String keyName;

    @ApiModelProperty("具体值")
    private String valueName;

    @ApiModelProperty("创建时间")
    private LocalDateTime createTime;
}
