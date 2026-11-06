package com.example.kb.config;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层配置：注册拦截器并声明拦截范围。
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class WebConfig implements WebMvcConfigurer {
    private final AuthInterceptor authInterceptor;


    /**
     * 拦截所有 /api/** 请求，放行 /api/auth/**（登录、注册、退出、me 自己判登录）
     * @param registry 拦截器注册表，由 Spring 传入
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        log.info("拦截器启动");
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**");

    }






}
