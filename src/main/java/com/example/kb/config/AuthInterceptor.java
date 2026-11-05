package com.example.kb.config;


import com.sun.net.httpserver.HttpsServer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.http.HttpResponse;

/**
 * 登录拦截器：校验 Session，把 userId 挂到请求属性上供 Controller 使用。
 * 只负责"进门验票"，不做任何业务判断。
 */
@Component
@Slf4j
public class AuthInterceptor implements HandlerInterceptor {
    /** 与 AuthController 里 session 的 key 保持一致 */
    private static final String SESSION_USER_ID = "userId";

    /**
     * 请求进入 Controller 之前执行
     * @param request  当前请求，用于读 session、写 userId 属性
     * @param response 当前响应（这里用不到，但接口要求保留）
     * @param handler  即将执行的 Controller 方法
     * @return true 表示放行；抛 401 表示拦截
     * @throws BusinessException 401，未登录或会话已失效
     */
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handle) {
        //跨域预检不带Cookie，必须放行
        /**
         * OPTIONS 是浏览器的"敲门问路"，它不带 Cookie。你拦它，等于把门卫拦在了自己家门口，后面真正持证的人也进不来。
         */
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }


        HttpSession session = request.getSession(false);

        Object userId = session == null ? null : session.getAttribute(SESSION_USER_ID);

        if(userId == null) {
            throw new BusinessException(401,"请先登录");
        }

        request.setAttribute(SESSION_USER_ID,userId);
        log.info("登录拦截器启动成功");
        return true;

    }



}
