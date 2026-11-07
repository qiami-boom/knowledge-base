package com.example.kb.service;


import com.example.kb.aspect.AutoTimestamp;
import com.example.kb.config.BusinessException;
import com.example.kb.entity.Knowledge;

import com.example.kb.mapper.KnowledgeMapper;
import com.example.kb.service.dto.KnowledgeRequest;
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

    public Knowledge add(KnowledgeRequest request){
        Knowledge knowledge = new Knowledge();

        knowledge.setTitle(request.title());
        knowledge.setContent(request.content());
        knowledge.setSource(
                request.source() == null || request.source().isBlank() ? "MANUAL" : request.source()
                //来源判空或无来源字段设置为MANUAL，无问题就设置正确来源
        );
        knowledge.setPersonalNote(request.personalNote());
        knowledge.setAiScore(0);

        LocalDateTime now = LocalDateTime.now();
        knowledge.setCreatedAt(now);
        knowledge.setUpdatedAt(now);

        knowledgeMapper.insert(knowledge);
        return knowledge;
    }
    @AutoTimestamp
    public void batchAdd(List<Knowledge> knowledges){
        knowledgeMapper.batchInsert(knowledges);
    }

    public Knowledge update(Long id,KnowledgeRequest request) {
        Knowledge existing = findById(id);

        existing.setTitle(request.title());
        existing.setContent(request.content());
        existing.setSource(request.source());
        existing.setCategory(request.category());
        existing.setTags(request.tags());
        existing.setStarRating(
                request.starRating() == null ? 0 : request.starRating()
        );
        existing.setPersonalNote(request.personalNote());
        existing.setUpdatedAt(LocalDateTime.now());

        knowledgeMapper.update(existing);
        return existing;
    }

    public void delete(Long id){
        findById(id);
        knowledgeMapper.deleteById(id);
    }

    @AutoTimestamp
    public void addDrafts(List<Knowledge> drafts){
        for(Knowledge knowledge : drafts){
            knowledgeMapper.insert(knowledge);
        }
    }

    /**
     * 用户点"入库"后调用。只把 status 从 DRAFT 改成 ACTIVE，不动内容字段。
     * 已经是 ACTIVE 的不能重复确认，抛 400。
     * @param id
     */
    public void confirmDraft(Long id) {
        Knowledge knowledge = findById(id);
        if (!"DRAFT".equals(knowledge.getStatus())) {   //”字符串“.equals()判断字符串与括号里面的东西是否完全相同
            throw new BusinessException(400, "只有草稿可以确认入库");
        }
        knowledge.setStatus("ACTIVE");
        knowledge.setUpdatedAt(LocalDateTime.now());
        knowledgeMapper.update(knowledge);
    }

    /**
     * 用户点"丢弃"后调用。草稿从未被认可过，直接物理删除，不做软删除。
     * 已经是 ACTIVE 的不能丢弃，抛 400。
     */
    public void discardDraft(Long id) {
        Knowledge knowledge = findById(id);
        if (!"DRAFT".equals(knowledge.getStatus())) {
            throw new BusinessException(400, "只有草稿可以丢弃");
        }
        knowledgeMapper.deleteById(id);
    }


}
