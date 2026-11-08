package com.example.kb.agent;

import com.example.kb.config.BusinessException;
import com.example.kb.config.Result;
import com.example.kb.service.dto.AgentChatRequest;
import com.example.kb.service.dto.AgentReply;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 对话入口。只做协议转换，编排逻辑全在 AgentService。
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    @Autowired
    private AgentService agentService;

    @PostMapping("/chat")
    public Result<AgentReply> chat(@RequestBody AgentChatRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new BusinessException(400, "消息不能为空");
        }
        return Result.success(agentService.chat(request));
    }
}
