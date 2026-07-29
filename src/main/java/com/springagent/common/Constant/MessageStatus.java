package com.springagent.common.Constant;

import lombok.Getter;

/**
 * 消息处理状态，取值需与 chat_message 表的 chat_message_status_chk 约束保持一致。
 */
@Getter
public enum MessageStatus {
    PENDING("待处理"),
    STREAMING("流式输出中"),
    COMPLETED("已完成"),
    FAILED("失败");

    private final String desc;

    MessageStatus(String desc) {
        this.desc = desc;
    }

}
