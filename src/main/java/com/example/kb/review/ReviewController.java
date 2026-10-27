
package com.example.kb.review;

import com.example.kb.config.Result;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
/**
 * 复习卡片的 HTTP 入口
 * 串联：前端 → 这里 → ReviewService → Mapper
 * 所有返回值都包 Result 信封（前端 axios 拦截器统一取 data.data）
 */
@RestController
@RequestMapping("/api/review")
@CrossOrigin(origins = "*")
public class ReviewController {
    @Autowired
    private ReviewService reviewService;

    @Operation(summary = "获取待复习卡片")
    @GetMapping("/due")
    public Result<List<ReviewCard>> due(){
        return Result.success(reviewService.dueCards());
    }

    @Operation(summary = "提交复习自评")
    @PostMapping("/{id}")
    public Result<ReviewCard> review(@PathVariable Long id, @RequestParam RecallQuality quality){
        return Result.success(reviewService.review(id,quality));
    }

    //新端点：为指定知识生成卡片(幂等，重复点不会花钱)
    @Operation(summary = "为指定知识生成复习卡（幂等）")
    @PostMapping("/cards/{knowledgeId}")
    public Result<List<ReviewCard>> createCards(@PathVariable Long knowledgeId){
        return Result.success(reviewService.createCards(knowledgeId));
    }

}
