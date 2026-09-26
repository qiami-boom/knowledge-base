package com.example.kb.service;


import com.example.kb.config.BusinessException;
import com.example.kb.entity.Knowledge;

import com.example.kb.mapper.KnowledgeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class KnowledgeService {

    @Autowired
    private KnowledgeMapper knowledgeMapper;

    public List<Knowledge> findAll() {return knowledgeMapper.findAll();}

public Knowledge findById (Long id){
    Knowledge knowledge = knowledgeMapper.findById(id);
    if(knowledge == null){
        throw new BusinessException(404,"知识不存在");
    }
    return knowledge;
}

    public Knowledge add(Knowledge knowledge){
        knowledge.setCreatedAt(LocalDateTime.now());
        knowledge.setUpdatedAt(LocalDateTime.now());
        knowledgeMapper.insert(knowledge);
        return knowledge;
    }
    public void batchAdd(List<Knowledge> knowledges){
        for(Knowledge knowledge : knowledges){
            knowledge.setCreatedAt(LocalDateTime.now());
            knowledge.setUpdatedAt(LocalDateTime.now());
        }
        knowledgeMapper.batchInsert(knowledges);
    }
    public Knowledge update(Long id,Knowledge knowledge) {
        Knowledge existing = findById(id);
        if (existing == null) {
            return null;
        }
        if (knowledge.getTitle() != null) {
            existing.setTitle(knowledge.getTitle());
        }
        if (knowledge.getContent() != null) {
            existing.setContent(knowledge.getContent());
        }
        if (knowledge.getCategory() != null) {
            existing.setCategory(knowledge.getCategory());
        }
        if (knowledge.getSource() != null) {
            existing.setSource(knowledge.getSource());
        }
        if (knowledge.getAiScore() != null) {
            existing.setAiScore(knowledge.getAiScore());
        }
        if (knowledge.getPersonalNote() != null) {
            existing.setPersonalNote(knowledge.getPersonalNote());
        }
        if (knowledge.getStarRating() != null) {
            existing.setStarRating(knowledge.getStarRating());
        }
        if (knowledge.getTags() != null) {
            existing.setTags(knowledge.getTags());
        }
        existing.setUpdatedAt(LocalDateTime.now());
        knowledgeMapper.update(existing);
        return existing;
    }
    public Boolean delete(Long id){
        Knowledge existing = findById(id);
        if(existing == null){
            return false;
        }
        knowledgeMapper.deleteById(id);
        return true;
    }
}
