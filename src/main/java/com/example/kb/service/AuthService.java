package com.example.kb.service;


import com.example.kb.common.AgentRule;
import com.example.kb.common.PasswordHasher;
import com.example.kb.config.BusinessException;
import com.example.kb.entity.User;
import com.example.kb.mapper.UserMapper;
import com.example.kb.service.dto.LoginRequest;
import com.example.kb.service.dto.RegisterRequest;
import com.example.kb.service.dto.UserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 认证业务：注册、登录、查当前用户、改 AI 风格。
 * 不返回 Result、不碰 Session，失败一律抛 BusinessException。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;

    /**
     * 注册：规范化 → 查重 → 组装 → 落库 → 转对外对象
     * @param request
     * @return UserInfo
     */
    public UserInfo register(RegisterRequest request){
        log.info("注册规范化开启");
        String username = normlize(request.getUsername());

        if(userMapper.findByUsername(username) != null) {
            throw new BusinessException(400,"用户名已经被占用");
        }


        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(PasswordHasher.encode(request.getPassword()));
        user.setDisplayName(displayName(request.getDisplayName(), username));
        user.setAgentRule(AgentRule.EXPLAIN.name());   // 默认讲解模式
        user.setCreatedAt(LocalDateTime.now());

        userMapper.insert(user);
        return toInfo(user);
    }


    /**
     * 登录：查库 → 比对。不存在与密码错共用同一提示，防枚举账号
     * @param request
     * @return UserInfo
     */
    public UserInfo login(LoginRequest request) {
        User user = userMapper.findByUsername(normlize(request.getUsername()));

        if(user == null || !PasswordHasher.matches(request.getPassword(),user.getPasswordHash())) {
            throw new BusinessException(401,"用户名或者密码错误");
        }

        return toInfo(user);
    }

    /**
     * 按 id 查用户，/me 与 agent-rule 用
      * @param userid
     * @return
     */
    public UserInfo findById(Long userid) {
        User user = userMapper.findById(userid);
        if(user == null) {
            throw new BusinessException(401,"请先登录");
        }
        return toInfo(user);
    }

    /**
     * 改 AI 风格，userId 来自 session，不信任前端传的 id
     * @param userId
     * @param rule
     */
    public void updateAgentRule(Long userId, AgentRule rule) {
        userMapper.updateAgentRule(userId, rule.name());
    }

    /**
     * 去空格 + 转小写；必须用 Locale.ROOT，避免不同语言环境结果不一致
     * @param raw
     * @return 返回优化好的字符串
     */
    private String normlize(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT);//还有国际化转换的事，nb
    }


    /**
     * 计算"显示名称"——如果传入的 raw 为空（null 或纯空白），就用 username 兜底；否则用 raw 去掉首尾空格后的值。
     * @param raw
     * @param username
     * @return
     */
    private String displayName(String raw, String username) {
        return (raw == null || raw.isBlank()) ? username : raw.trim();
    }

    /**
     * 实体 → 对外对象，丢掉 passwordHash 和 createdAt
     * @param user
     * @return
     */
    private UserInfo toInfo(User user) {
        return new UserInfo(user.getId(), user.getUsername(), user.getDisplayName(), user.getAgentRule());
    }


}
