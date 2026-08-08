package com.springagent.admin.domain.vo;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class ConversationListVO {
    private String userName;
    private UUID userId;
    private Long conversationCount;
    private UUID[] conversationIds;
}
