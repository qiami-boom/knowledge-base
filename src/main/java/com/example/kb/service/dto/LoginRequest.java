package com.example.kb.service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求体。
 * 前端 POST /api/auth/login 发来的 JSON，由 Spring+Jackson 自动装进这个对象。
 * 只开放 username / password 两个字段，前端无法往这里塞别的值。
 */
@Data
public class LoginRequest {

    /** 用户名：不能为空（null、空串、纯空格都算空） */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 密码：不能为空。这里不校验长度和格式，因为登录失败统一按 401 处理 */
    @NotBlank(message = "密码不能为空")
    private String password;
}
