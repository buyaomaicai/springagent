package com.springagent.admin.controller;

import com.springagent.admin.domain.vo.ConversationListVO;
import com.springagent.admin.domain.vo.ConversationVO;
import com.springagent.common.api.ApiResponse;
import com.springagent.common.api.ErrorCode;
import com.springagent.common.exception.BusinessException;
import com.springagent.diagnosis.entity.ChatConversation;
import com.springagent.diagnosis.service.IChatConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class adminController {
    private final IChatConversationService chatConversationService;
    @GetMapping("/message")
    public ApiResponse<List<ConversationListVO>> message() {
        return ApiResponse.success(chatConversationService.getConversionList());
    }
    @GetMapping("/message/detail/{id}")
    public ApiResponse<List<ConversationVO>> getConversionDetail(@PathVariable UUID id){
        return ApiResponse.success(chatConversationService.getConversionByUserId(id));
    }
}
