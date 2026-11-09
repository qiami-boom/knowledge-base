package com.example.kb.service;


import com.example.kb.aspect.AutoTimestamp;
import com.example.kb.config.BusinessException;
import com.example.kb.entity.Knowledge;

import com.example.kb.knowledge_tag.service.TagServer;
import com.example.kb.mapper.KnowledgeMapper;
import com.example.kb.service.dto.KnowledgeAiEditResult;
import com.example.kb.service.dto.KnowledgeRequest;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class KnowledgeService {

    private static final String ORIGINAL_CONTENT_MARKER = "\n\n---\n\n## 原文\n\n";

    @Autowired
    private KnowledgeMapper knowledgeMapper;

    @Autowired
    private AiService aiService;

    @Autowired
    private TagServer tagServer;

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
        knowledge.setCategory(request.category());
        knowledge.setTags(request.tags());
        knowledge.setStarRating(request.starRating() == null ? 0 : request.starRating());
        knowledge.setPersonalNote(request.personalNote());
        knowledge.setAiScore(0);

        // 归属用户，必须在 insert 之前设置
        knowledge.setUserId(userId);

        LocalDateTime now = LocalDateTime.now();
        knowledge.setCreatedAt(now);
        knowledge.setUpdatedAt(now);

        knowledgeMapper.insert(knowledge);
        applyAiTagsIfEmpty(knowledge);
        return knowledge;
    }

    /** 批量写入（导入用），统一设置归属用户 */
    @AutoTimestamp
    public void batchAdd(List<Knowledge> knowledges, Long userId) {
        for (Knowledge knowledge : knowledges) {
            knowledge.setUserId(userId);
            knowledgeMapper.insert(knowledge);
            applyAiTagsIfEmpty(knowledge);
        }
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

    /**
     * 使用固定指令精简一张知识卡。
     */
    public Knowledge condense(Long id, boolean keepOriginal, Long userId) {
        return aiEdit(
                id,
                "在不丢失关键事实、代码、命令和边界条件的前提下精简这张知识卡，去掉重复表达和对话痕迹，保留 Markdown 结构。",
                keepOriginal,
                userId
        );
    }

    /**
     * AI 编辑已有知识卡。
     * 只允许修改 title/content/summary/category/tags，其他人工字段保持不变。
     */
    public Knowledge aiEdit(Long id, String instruction, boolean keepOriginal, Long userId) {
        if (instruction == null || instruction.isBlank()) {
            throw new BusinessException(400, "编辑指令不能为空");
        }
        if (instruction.length() > 500) {
            throw new BusinessException(400, "编辑指令不能超过 500 个字符");
        }

        Knowledge existing = findById(id, userId);
        if (!"ACTIVE".equals(existing.getStatus())) {
            throw new BusinessException(400, "只有已入库的知识可以交给 AI 编辑");
        }

        KnowledgeAiEditResult edit = aiService.editKnowledge(existing, instruction.trim());
        if (edit == null
                || edit.title() == null || edit.title().isBlank()
                || edit.content() == null || edit.content().isBlank()) {
            throw new BusinessException(502, "AI 没有生成有效的知识卡，请稍后重试");
        }
        if (edit.title().length() > 200 || edit.content().length() > 20000) {
            throw new BusinessException(502, "AI 生成的内容超出长度限制，请重试");
        }

        String condensedContent = condensedContentOnly(edit.content().trim());
        if (condensedContent.isBlank()) {
            throw new BusinessException(502, "AI 生成的精简内容为空，请重试");
        }

        String nextContent = condensedContent;
        if (keepOriginal) {
            String original = extractOriginalContent(existing.getContent());
            nextContent = "## AI 精简版\n\n" + condensedContent
                    + ORIGINAL_CONTENT_MARKER + original;
        }
        if (nextContent.length() > 20000) {
            throw new BusinessException(502, "保留原文后内容超过长度限制，请选择替换原文");
        }

        existing.setTitle(edit.title().trim());
        existing.setContent(nextContent);
        existing.setSummary(trimToNull(edit.summary(), 200));
        if (edit.category() != null && !edit.category().isBlank()) {
            existing.setCategory(trimToNull(edit.category(), 50));
        }
        if (edit.tags() != null && !edit.tags().isBlank()) {
            existing.setTags(trimToNull(edit.tags(), 500));
        }
        existing.setUpdatedAt(LocalDateTime.now());

        knowledgeMapper.update(existing);
        return existing;
    }

    /**
     * 为已有知识重新生成标签，用于补齐导入或历史数据。
     */
    public Knowledge generateTags(Long id, Long userId) {
        Knowledge knowledge = findById(id, userId);
        if (!"ACTIVE".equals(knowledge.getStatus())) {
            throw new BusinessException(400, "只有已入库的知识可以生成标签");
        }

        List<String> tags = tagServer.tag(tagContent(knowledge), id);
        if (tags.isEmpty()) {
            throw new BusinessException(502, "AI 没有生成有效标签，请稍后重试");
        }

        knowledge.setTags(String.join(",", tags));
        knowledge.setUpdatedAt(LocalDateTime.now());
        knowledgeMapper.update(knowledge);
        return knowledge;
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
        applyAiTagsIfEmpty(knowledge);
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

    public List<Knowledge> search(String keyword, int limit, Long userId) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return knowledgeMapper.searchByKeyword(keyword.trim(), limit, userId);
    }

    private String trimToNull(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private String extractOriginalContent(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        int markerIndex = content.lastIndexOf(ORIGINAL_CONTENT_MARKER);
        return markerIndex >= 0
                ? content.substring(markerIndex + ORIGINAL_CONTENT_MARKER.length())
                : content;
    }

    private String condensedContentOnly(String content) {
        if (content == null) return "";
        int headingIndex = content.indexOf("## 原文");
        return (headingIndex >= 0 ? content.substring(0, headingIndex) : content).strip();
    }

    private void applyAiTagsIfEmpty(Knowledge knowledge) {
        if (knowledge.getTags() != null && !knowledge.getTags().isBlank()) {
            return;
        }
        try {
            List<String> tags = tagServer.tag(tagContent(knowledge), knowledge.getId());
            if (!tags.isEmpty()) {
                knowledge.setTags(String.join(",", tags));
                knowledge.setUpdatedAt(LocalDateTime.now());
                knowledgeMapper.update(knowledge);
            }
        } catch (Exception e) {
            log.warn("自动生成标签失败，知识卡仍保留：id={}, reason={}",
                    knowledge.getId(), e.getMessage());
        }
    }

    private String tagContent(Knowledge knowledge) {
        String title = knowledge.getTitle() == null ? "" : knowledge.getTitle();
        String content = knowledge.getContent() == null ? "" : knowledge.getContent();
        String summary = knowledge.getSummary() == null ? "" : knowledge.getSummary();
        return (title + "\n" + summary + "\n" + content).trim();
    }

}
