package com.example.kb.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对应数据库 user 表的一行。
 *
 * 字段用驼峰命名，靠 application.properties 里的
 * map-underscore-to-camel-case=true 自动映射：
 *   password_hash → passwordHash
 *   display_name  → displayName
 *   agent_rule    → agentRule
 *   created_at    → createdAt
 */
@Data
public class User {

    /** 自增主键。用包装类 Long 而非 long：插入前为 null，能明确表示"还没有 id" */
    private Long id;

    /** 登录名。库里存的是 Service 层规范化后的小写形式 */
    private String username;

    /** BCrypt 哈希值（$2a$10$ 开头，60 个字符），绝不对外返回 */
    private String passwordHash;

    /** 展示名。注册时没填则回退为 username */
    private String displayName;

    /** AI 回答风格，存枚举名：EXPLAIN / EXAM / SOCRATIC / CUSTOM */
    private String agentRule;

    /** 注册时间。由 Java 侧 LocalDateTime.now() 写入，库里存 ISO-8601 文本 */
    private LocalDateTime createdAt;
}
