package com.example.kb.controller;

import com.example.kb.config.Result;
import com.example.kb.entity.Knowledge;
import com.example.kb.mapper.KnowledgeMapper;
import com.example.kb.review.ReviewService;
import com.example.kb.service.KnowledgeService;
import com.example.kb.service.dto.KnowledgeRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识库 HTTP 入口。
 * 当前用户 id 由 AuthInterceptor 写入 request 属性，这里统一取用后传给 Service；
 * 不信任任何前端传入的 userId。
 */
@Slf4j
@RestController
@RequestMapping("/api/knowledges")
@CrossOrigin(origins = "*")
public class KnowledgeController {

    @Autowired
    private KnowledgeService knowledgeService;

    @Autowired
    private ReviewService reviewService;

    /** 统一取拦截器写入的当前用户 id */
    private Long uid(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }

    @GetMapping
    public Result<List<Knowledge>> getAll(HttpServletRequest httptRequest){
        return Result.success(knowledgeService.findAll(uid(httptRequest)));
    }

    @GetMapping("/{id}")
    public Result<Knowledge> findById(@PathVariable Long id,HttpServletRequest httpRequest){
        Knowledge knowledge = knowledgeService.findById(id, uid(httpRequest));
        return Result.success(knowledge);
    }

    @PostMapping//@Valid 表示“让 Spring 对这个 DTO 执行字段校验”。如果没有 @Valid，字段上的 @NotBlank 等规则不会生效
    public Result<Knowledge> add(@Valid @RequestBody KnowledgeRequest request,
                                 HttpServletRequest httpRequest) {
        return Result.success(knowledgeService.add(request, uid(httpRequest)));
    }

    @PutMapping("/{id}")
    public Result<Knowledge> update(@PathVariable Long id,
                                    @Valid @RequestBody KnowledgeRequest request,
                                    HttpServletRequest httpRequest){
        return Result.success(knowledgeService.update(id,request, uid(httpRequest)));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest httpRequest){
        knowledgeService.delete(id, uid(httpRequest));
        return Result.success();
    }

    @PostMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long userId = uid(httpRequest);
        knowledgeService.confirmDraft(id, userId);

        // 入库后顺手建复习卡，补上"存了却不会复习"的断裂。
        // 建卡要调 AI，可能失败；失败不影响入库，用户还能在详情页手动补。
        try {
            reviewService.createCards(id, userId);
        } catch (Exception e) {
            log.warn("自动建卡失败，不影响入库：{}", e.getMessage());
        }

        return Result.success(null);
    }

    @PostMapping("/{id}/discard")
    public Result<Void> discard(@PathVariable Long id, HttpServletRequest httpRequest) {
        knowledgeService.discardDraft(id, uid(httpRequest));
        return Result.success(null);
    }
}
