package com.example.kb.controller;

import com.example.kb.entity.Knowledge;
import com.example.kb.service.KnowledgeService;
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
    public List<Knowledge> getAll(){return knowledgeService.findAll();}

    @GetMapping("/{id}")
    public Knowledge findById(@PathVariable Long id){return knowledgeService.findById(id);}

    @PostMapping
    public Knowledge add(@RequestBody Knowledge knowledge){
        return knowledgeService.add(knowledge);
    }

    @PutMapping("/{id}")
    public Knowledge update(@PathVariable Long id,@RequestBody Knowledge knowledge){
        return knowledgeService.update(id,knowledge);
    }

    @DeleteMapping("/{id}")
    public Boolean delete(@PathVariable Long id){return knowledgeService.delete(id);}
}
