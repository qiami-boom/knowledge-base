package com.example.kb.knowledge_tag.service.impl;

import com.example.kb.knowledge_tag.commom.Tag;
import com.example.kb.knowledge_tag.mapper.TagMapper;
import com.example.kb.knowledge_tag.service.TagServer;
import com.example.kb.knowledge_tag.util.TagNormalizer;
import com.example.kb.service.AiService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

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
    public List<String> tag(String content, Long knowledgeId) {
        // 1. AI 生成候选标签
        List<String> aiTags = aiService.generateTags(content, 5);

        // 2. 逐个匹配库内已有标签
        for (String aiTag : aiTags) {

            String norm = TagNormalizer.normalize(aiTag);    // "Spring Boot" → "springboot"
            if (norm.isEmpty()) continue;

            // L0 精确匹配
            Tag tag = tagMapper.findByNormalized(norm);
            // L1 别名匹配（DI ↔ 依赖注入）
            if (tag == null) {
                tag = tagMapper.findByAliasNormalized(norm);
            }

            if (tag == null) {
                // 库里没有 → 新建
                tag = new Tag();
                tag.setName(aiTag);
                tag.setNormalized(norm);
                tag.setCreatedAt(LocalDateTime.now());
                tagMapper.insertTag(tag);
            } else {
                // 有 → 复用，计数 +1
                tagMapper.increaseUsage(tag.getId());
            }

            // 记录 笔记↔标签 关联
            tagMapper.link(knowledgeId, tag.getId());
        }

        return aiTags;
    }
}
