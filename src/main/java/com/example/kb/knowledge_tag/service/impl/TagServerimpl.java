package com.example.kb.knowledge_tag.service.impl;

import com.example.kb.knowledge_tag.commom.Tag;
import com.example.kb.knowledge_tag.mapper.TagMapper;
import com.example.kb.knowledge_tag.service.TagServer;
import com.example.kb.knowledge_tag.util.TagNormalizer;
import com.example.kb.service.AiService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class TagServerimpl implements TagServer {
    private final AiService aiService;      // 调 AI
    private final TagMapper tagMapper;      // 查/写库

    public TagServerimpl(AiService aiService, TagMapper tagMapper) {
        this.aiService = aiService;
        this.tagMapper = tagMapper;
    }
    /**
     * 给一篇笔记打标签：AI 生成候选 → 查库匹配 → 没有就新建 → 记录关联
     *
     * @param content     笔记标题+内容（喂给ai）
     * @param knowledgeId 笔记id
     * @return 打上的标签名列表
     */
    @Override
    @Transactional
    public List<String> tag(String content, Long knowledgeId) {
        return persistTags(aiService.generateTags(content, 3), knowledgeId);
    }

    @Override
    @Transactional
    public List<String> linkTags(List<String> tags, Long knowledgeId) {
        return persistTags(tags, knowledgeId);
    }

    private List<String> persistTags(List<String> tags, Long knowledgeId) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }

        Set<String> seen = new LinkedHashSet<>();
        List<String> assignedNames = new java.util.ArrayList<>();

        // 先清理旧关系，再按本次结果重建
        tagMapper.deleteLinksByKnowledgeId(knowledgeId);
        for (String rawTag : tags) {
            if (rawTag == null || rawTag.isBlank()) continue;

            String displayName = rawTag.trim();
            String norm = TagNormalizer.normalize(displayName);
            if (norm.isEmpty() || !seen.add(norm)) continue;

            // L0 精确匹配
            Tag tag = tagMapper.findByNormalized(norm);
            // L1 别名匹配（DI ↔ 依赖注入）
            if (tag == null) {
                tag = tagMapper.findByAliasNormalized(norm);
            }

            if (tag == null) {
                // 库里没有 → 新建
                tag = new Tag();
                tag.setName(displayName);
                tag.setNormalized(norm);
                tag.setUsageCount(0);
                tag.setCreatedAt(LocalDateTime.now());
                tagMapper.insertTag(tag);
            }

            // 记录 笔记↔标签 关联
            tagMapper.link(knowledgeId, tag.getId());
            assignedNames.add(tag.getName());
            if (assignedNames.size() == 3) break;
        }

        // usage_count 统一按关系表重算，避免复用和重生成时计数漂移
        tagMapper.recalculateUsage();
        return assignedNames;
    }
}
