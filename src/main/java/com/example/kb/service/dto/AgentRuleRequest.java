package com.example.kb.service.dto;

import com.example.kb.common.AgentRule;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 修改 AI 行为规则的请求体。
 * 对应 PUT /api/user/agent-rule，body 形如 {"agentRule": "SOCRATIC"}。
 */
@Data
public class AgentRuleRequest {

    /**
     * 只能是 EXPLAIN / EXAM / SOCRATIC / CUSTOM 之一。
     * 类型直接写成枚举，Jackson 遇到其它字符串会抛异常，
     * 由全局异常处理器转成 400，所以这里不用再写"值是否合法"的校验。
     */
    @NotNull(message = "agentRule 不能为空")
    private AgentRule agentRule;
}
