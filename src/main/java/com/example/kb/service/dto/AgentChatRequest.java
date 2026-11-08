package com.example.kb.service.dto;

import java.util.List;

/**
 * Agent 对话请求：
 * message    用户这句话
 * history    最近几条历史（可以为空）
 * customRule 用户自定义的回答风格（可为空）。只在 agentRule=CUSTOM 时有意义，
 *            随请求传而不是存库：它是一次性的偏好，换设备丢了也无所谓
 */
public record AgentChatRequest(String message, List<AgentMessage> history, String customRule) {
}
