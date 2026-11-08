package com.example.kb.service.dto;

import java.util.List;

/**
 * Agent 对话请求：
 * message 是用户这句话，history 是最近几条历史（可以为空）。
 */
public record AgentChatRequest(String message, List<AgentMessage> history) {
}
