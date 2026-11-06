package com.example.kb.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求体。
 * 这里只做"格式"校验；真正的业务规则（转小写、查重、加密）在 AuthService 里。
 */
@Data
public class RegisterRequest {

    /**
     * 用户名。
     * 这里故意允许大写，因为"大小写不敏感"是 Service 层转小写实现的；
     * 如果这里只收小写，用户输入 Zhang 会被直接 400，体验很差。
     */
    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_]{3,20}$",
            message = "用户名只能是 3-20 位字母、数字、下划线")
    private String username;

    /**
     * 密码。
     * 上限 64 是为了给 BCrypt 的 72 字节上限留余量，避免多字节字符被静默截断。
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度必须是 8-64 位")
    private String password;

    /** 展示名。可空；为空时由 AuthService 回退成 username */
    private String displayName;
}
