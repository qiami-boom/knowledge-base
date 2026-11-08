package com.example.kb.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 前端单页应用（Vue + history 路由）的服务端兜底。
 *
 * 打包部署后，frontend/dist 会被拷进 classpath:/static/，浏览器里点导航是前端自己切路由，
 * 但那几个地址一旦被"刷新"或"直接粘贴打开"，请求就真的打到后端了：
 * 后端没有 /chat、/detail/1 这样的接口，会被当成接口不存在，返回一段 JSON 404。
 *
 * 这里只做一件事：把这些前端路由转发到 index.html，让前端自己接管。
 *
 * 注意两点：
 * 1) 不要写成 /** 通配——那会抢掉 /api/** 和 swagger 的映射；
 * 2) 以后前端加了新页面（router/index.js 里新增 path），记得在这里补一行。
 */
@Controller
public class SpaForwardController {

    @GetMapping({"/", "/chat", "/review", "/login", "/detail/{id}"})
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
