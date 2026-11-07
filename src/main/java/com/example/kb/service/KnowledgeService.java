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

/**
 * 知识库业务层。
 * 所有方法都要求显式传入 userId：查询按归属过滤，写入带归属，
 * 越权时统一表现为 404「知识不存在」，不区分"不存在"与"无权访问"。
 */
@Service
public class KnowledgeService {

    @Autowired
    private KnowledgeMapper knowledgeMapper;

    /** 当前用户的正式知识列表（status=ACTIVE） */
    public List<Knowledge> findAll(Long userId) {
        return knowledgeMapper.findAll(userId);
    }

    /**
     * 按 id 查当前用户的知识
     * @param id 知识 id
     * @param userId 当前登录用户 id
     * @return 知识实体
     * @throws BusinessException 404，知识不存在或不属于当前用户
     */
    public Knowledge findById(Long id, Long userId) {
        Knowledge knowledge = knowledgeMapper.findById(id, userId);
        if (knowledge == null) {
            throw new BusinessException(404, "知识不存在");
        }
        return knowledge;
    }

    /** 手动新增：组装字段并落上归属用户 */
    public Knowledge add(KnowledgeRequest request, Long userId) {
        Knowledge knowledge = new Knowledge();

        knowledge.setTitle(request.title());
        knowledge.setContent(request.content());
        knowledge.setSource(
                request.source() == null || request.source().isBlank() ? "MANUAL" : request.source()
                //来源判空或无来源字段设置为MANUAL，无问题就设置正确来源
        );
        knowledge.setPersonalNote(request.personalNote());
        knowledge.setAiScore(0);

        // 归属用户，必须在 insert 之前设置
        knowledge.setUserId(userId);

        LocalDateTime now = LocalDateTime.now();
        knowledge.setCreatedAt(now);
        knowledge.setUpdatedAt(now);

        knowledgeMapper.insert(knowledge);
        return knowledge;
    }

    /** 批量写入（导入用），统一设置归属用户 */
    @AutoTimestamp
    public void batchAdd(List<Knowledge> knowledges, Long userId) {
        knowledges.forEach(knowledge -> knowledge.setUserId(userId));
        knowledgeMapper.batchInsert(knowledges);
    }

    /** 更新：先按归属查，越权时 404 */
    public Knowledge update(Long id, KnowledgeRequest request, Long userId) {
        Knowledge existing = findById(id, userId);
        existing.setUserId(userId);   // 保证 UPDATE 的 WHERE 能命中

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

    /** 删除：先按归属查，越权时 404 */
    public void delete(Long id, Long userId) {
        findById(id, userId);
        knowledgeMapper.deleteById(id, userId);
    }

    /** 写入抽取出来的草稿（status=DRAFT），统一设置归属用户 */
    @AutoTimestamp
    public void addDrafts(List<Knowledge> drafts, Long userId) {
        for (Knowledge knowledge : drafts) {
            knowledge.setUserId(userId);
            knowledgeMapper.insert(knowledge);
        }
    }

    /**
     * 用户点"入库"后调用。只把 status 从 DRAFT 改成 ACTIVE，不动内容字段。
     * 已经是 ACTIVE 的不能重复确认，抛 400。
     * @param id 草稿 id
     * @param userId 当前登录用户 id
     */
    public void confirmDraft(Long id, Long userId) {
        Knowledge knowledge = findById(id, userId);
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
     * @param id 草稿 id
     * @param userId 当前登录用户 id
     */
    public void discardDraft(Long id, Long userId) {
        Knowledge knowledge = findById(id, userId);
        if (!"DRAFT".equals(knowledge.getStatus())) {
            throw new BusinessException(400, "只有草稿可以丢弃");
        }
        knowledgeMapper.deleteById(id, userId);
    }


}
