package com.example.kb.service.dto;

/**
 * 对话历史里的一条消息。role 只有 user / assistant 两种。
 * 前端从 localStorage 里取最近几条传上来，后端不存会话。
 */
public record AgentMessage(String role, String content) {
}
