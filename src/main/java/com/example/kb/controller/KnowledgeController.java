package com.example.kb.controller;

import com.example.kb.config.Result;
import com.example.kb.entity.Knowledge;
import com.example.kb.mapper.KnowledgeMapper;
import com.example.kb.service.KnowledgeService;
import com.example.kb.service.dto.KnowledgeRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/knowledges")
@CrossOrigin(origins = "*")
public class KnowledgeController {
    @Autowired
    private KnowledgeService knowledgeService;

    @GetMapping
    public Result<List<Knowledge>> getAll(){
        return Result.success(knowledgeService.findAll());
    }

    @GetMapping("/{id}")
    public Result<Knowledge> findById(@PathVariable Long id){
        Knowledge knowledge = knowledgeService.findById(id);    
        return Result.success(knowledge);
    }

    @PostMapping//@Valid 表示“让 Spring 对这个 DTO 执行字段校验”。如果没有 @Valid，字段上的 @NotBlank 等规则不会生效
    public Result<Knowledge> add(@Valid @RequestBody KnowledgeRequest request) {
        return Result.success(knowledgeService.add(request));
    }

    @PutMapping("/{id}")
    public Result<Knowledge> update(@PathVariable Long id,
                                    @Valid @RequestBody KnowledgeRequest request){
        return Result.success(knowledgeService.update(id,request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id){
        knowledgeService.delete(id);
        return Result.success();
    }
}
