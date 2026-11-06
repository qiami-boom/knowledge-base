package com.example.kb.controller;


import com.example.kb.config.BusinessException;
import com.example.kb.config.Result;
import com.example.kb.service.AuthService;
import com.example.kb.service.dto.LoginRequest;
import com.example.kb.service.dto.RegisterRequest;
import com.example.kb.service.dto.UserInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 认证接口：注册、登录、退出、当前用户。
 * 只做协议转换（URL / JSON / Session），业务判断全部交给 AuthService。
 */

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String SESSION_USER_ID = "userId";

    private final AuthService authService;

    /**
     * 注册：接收 JSON → 校验格式 → 交给 Service 落库
     * @param request 注册请求，含用户名、密码、可选昵称
     * @return 新用户的可对外信息（不含密码哈希）
     */
    @PostMapping("/register")
    public Result<UserInfo> register(@Valid @RequestBody RegisterRequest request) {
        log.info("注册开始，账户：{}",request.getUsername());
        return Result.success(authService.register(request));
    }

    /**
     * 登录：校验账号密码 → 写入 session（只存 userId）→ 返回用户信息
     * @param request 登录请求，含用户名与密码
     * @param session 由 Spring 注入；登录成功才会被写入
     * @return 登录用户的可对外信息
     */
    @PostMapping("/login")
    public Result<UserInfo> login(@Valid @RequestBody LoginRequest request , HttpSession session) {
        log.info("开始登录，账户：{}",request.getUsername());
        UserInfo info = authService.login(request);
        session.setAttribute(SESSION_USER_ID,info.id());
        return Result.success(info);
    }






    /**
     * 退出：销毁 session。本来就没登录也返回成功，保证幂等
     * @param request 用于取当前 session，取不到就不处理
     * @return 空数据的成功响应
     */
    @PostMapping("/logout")
    public Result<Void> logout (HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if(session != null) {
            log.info("退出登录，销毁会话：{}", session.getId());
            session.invalidate();
        }else {
            log.info("退出登录：当前没有会话，直接返回成功");
        }

        return Result.success();

    }


    @GetMapping("/me")
    public Result<UserInfo> me (HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null ||session.getAttribute(SESSION_USER_ID) == null) {
            throw new BusinessException(401,"请先登录");
        }

        Long userId = (Long) session.getAttribute(SESSION_USER_ID);
        return Result.success(authService.findById(userId));
    }








    








}
