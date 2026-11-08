package com.example.kb.service.dto;

import java.util.List;

/**
 * Agent 回复：
 * reply  —— 模型最终要说的话
 * cards  —— 工具执行过程中产出的草稿卡片，交给前端渲染
 * steps  —— 调用轨迹（如"调用 extract_knowledge"），给前端显示步骤条
 */
public record AgentReply(String reply, List<ExtractResult.Item> cards, List<String> steps) {
}
