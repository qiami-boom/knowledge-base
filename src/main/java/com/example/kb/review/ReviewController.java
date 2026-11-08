package com.example.kb.review;

import com.example.kb.config.Result;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 复习卡片的 HTTP 入口
 * 串联：前端 → 这里 → ReviewService → Mapper
 * 所有返回值都包 Result 信封（前端 axios 拦截器统一取 data.data）
 * 当前用户 id 统一从拦截器写入的 request 属性取，不信任前端传参
 */
@RestController
@RequestMapping("/api/review")
public class ReviewController {

    @Autowired
    private ReviewService reviewService;

    /** 统一取拦截器写入的当前用户 id */
    private Long uid(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }

    @Operation(summary = "获取待复习卡片")
    @GetMapping("/due")
    public Result<List<ReviewCard>> due(HttpServletRequest httpRequest) {
        return Result.success(reviewService.dueCards(uid(httpRequest)));
    }

    @Operation(summary = "提交复习自评")
    @PostMapping("/{id}")
    public Result<ReviewCard> review(@PathVariable Long id,
                                     @RequestParam RecallQuality quality,
                                     HttpServletRequest httpRequest) {
        return Result.success(reviewService.review(id, quality, uid(httpRequest)));
    }

    //新端点：为指定知识生成卡片(幂等，重复点不会花钱)
    @Operation(summary = "为指定知识生成复习卡（幂等）")
    @PostMapping("/cards/{knowledgeId}")
    public Result<List<ReviewCard>> createCards(@PathVariable Long knowledgeId,
                                                HttpServletRequest httpRequest) {
        return Result.success(reviewService.createCards(knowledgeId, uid(httpRequest)));
    }

}
