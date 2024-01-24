package com.bb.bot.database.chatHistory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天消息历史记录对象 chat_history
 *
 * @author rym
 * @date 2023-10-26
 */
@Data
@ApiModel("聊天消息历史记录")
@TableName(value = "chat_history")
public class ChatHistory {
    private static final long serialVersionUID = 1L;

    @ApiModelProperty("主键")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    @ApiModelProperty("消息Id")
    private String messageId;

    @ApiModelProperty("用户qq号")
    private String userQq;

    @ApiModelProperty("群号")
    private String groupId;

    @ApiModelProperty("消息")
    private String text;

    @ApiModelProperty("类型")
    private String type;

    @ApiModelProperty("创建时间")
    private LocalDateTime createTime;

}
