package com.example.kb.controller;

import com.example.kb.config.Result;
import com.example.kb.entity.Knowledge;
import com.example.kb.service.KnowledgeService;
import com.example.kb.service.dto.KnowledgeRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识库 HTTP 入口。
 * 当前用户 id 由 AuthInterceptor 写入 request 属性，这里统一取用后传给 Service；
 * 不信任任何前端传入的 userId。
 */
@RestController
@RequestMapping("/api/knowledges")
public class KnowledgeController {

    @Autowired
    private KnowledgeService knowledgeService;

    /** 统一取拦截器写入的当前用户 id */
    private Long uid(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }

    @GetMapping
    public Result<List<Knowledge>> getAll(HttpServletRequest httpRequest) {
        return Result.success(knowledgeService.findAll(uid(httpRequest)));
    }

    @GetMapping("/{id}")
    public Result<Knowledge> findById(@PathVariable Long id, HttpServletRequest httpRequest) {
        return Result.success(knowledgeService.findById(id, uid(httpRequest)));
    }

    @PostMapping
    public Result<Knowledge> add(@Valid @RequestBody KnowledgeRequest request,
                                 HttpServletRequest httpRequest) {
        return Result.success(knowledgeService.add(request, uid(httpRequest)));
    }

    @PutMapping("/{id}")
    public Result<Knowledge> update(@PathVariable Long id,
                                    @Valid @RequestBody KnowledgeRequest request,
                                    HttpServletRequest httpRequest) {
        return Result.success(knowledgeService.update(id, request, uid(httpRequest)));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        knowledgeService.delete(id, uid(httpRequest));
        return Result.success();
    }

    @PostMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id, HttpServletRequest httpRequest) {
        knowledgeService.confirmDraft(id, uid(httpRequest));
        return Result.success(null);
    }

    @PostMapping("/{id}/discard")
    public Result<Void> discard(@PathVariable Long id, HttpServletRequest httpRequest) {
        knowledgeService.discardDraft(id, uid(httpRequest));
        return Result.success(null);
    }
}
