package com.example.kb.agent;

import java.util.Map;

/**
 * 一个可以被模型调用的工具。
 * 四个方法里，前三个是给模型看的"说明书"，最后一个才是真正的执行。
 */
public interface AgentTool {

    /** 工具名。模型靠它来指定调用谁，必须全局唯一，只用小写字母和下划线 */
    String name();

    /** 直接决定模型调不调 */
    String description();

    /** 作为JSON对象被原样拼进请求体的 tools 字段 */
    Map<String, Object> parameters();

    /** 执行。argumentsJson 是模型给的参数 JSON 原文，返回给模型看的结果 */
    ToolResult execute(String argumentsJson);
}
