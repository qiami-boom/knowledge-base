package com.example.kb.controller;

import com.example.kb.config.Result;
import com.example.kb.service.AuthService;
import com.example.kb.service.dto.AgentRuleRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.digester.RuleSet;
import org.springframework.web.bind.annotation.*;

/**
 * 用户设置接口：读写 AI 行为规则。
 * userId 由 AuthInterceptor 从 session 取出并写入请求属性，这里只负责取用。
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final AuthService authService;

    /**
     * 查询当前用户的 AI 规则：回库查最新值，不读 session 里的旧值
     * @param userId 拦截器写入的当前登录用户 id
     * @return 规则枚举名，如 EXPLAIN / EXAM / SOCRATIC / CUSTOM
     */
    @GetMapping("/agent-rule")
    public Result<String> getAgentRule(@RequestAttribute("userId") Long userId) {
        return Result.success(authService.findById(userId).agentRule());
    }


    /**
     * 修改当前用户的 AI 规则
     * @param userId 拦截器写入的当前登录用户 id，不信任前端传参
     * @param request 请求体，agentRule 只能是四个枚举值之一
     * @return 空数据的成功响应
     */
    @PutMapping("/agent-rule")
    public Result<String> updateAgentRule(@RequestAttribute("userId") Long userId,
                                          @Valid @RequestBody AgentRuleRequest request) {
        authService.updateAgentRule(userId,request.getAgentRule());
        return Result.success();
    }















}
