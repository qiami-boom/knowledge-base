package com.example.kb.service.dto;

/**
 * 用户信息响应体（只出不进）。
 *
 * 故意不包含 passwordHash / createdAt：这两个字段不能出网，
 * 否则密码哈希会出现在浏览器的 Network 面板里。
 *
 * 用 record 是因为它只有"装数据 + 读数据"的需求，不需要 setter。
 * 取值写 info.id()，不带 get 前缀，这是 record 的语法。
 */
public record UserInfo(Long id, String username, String displayName, String agentRule) {
}
