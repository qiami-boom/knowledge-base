package com.example.kb.agent;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工具注册表，agent循环中拿到工具要用他找到一个能执行的对象，
 * 再把“一共有哪些工具、每个工具要什么参数”打包给模型
 * 工具注册表：Spring 启动时收集所有实现了 AgentTool 的 bean。
 * 加一个新工具只要新建类加 @Component，这里一行都不用改。
 */
@Component
public class AgentToolRegistry {

    private final List<AgentTool> tools;
    private final Map<String, AgentTool> byName;

    //spring启动时找有实现这个接口的@Component,会把它们都塞进这个列表
    public AgentToolRegistry(List<AgentTool> tools) {
        this.tools = tools;
        // 重名会在启动时抛 IllegalStateException，比运行期调错工具好
        this.byName = tools.stream()
                .collect(Collectors.toMap(AgentTool::name, Function.identity()));
    }

    /** 给循环拼 tools 数组用 */
    public List<AgentTool> all() {
        return tools;
    }

    /** 按名字找，找不到返回 null；模型可能编出不存在的名字 */
    public AgentTool find(String name) {
        return byName.get(name);
    }
}