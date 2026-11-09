package com.example.kb.knowledge_tag.service;

import java.util.List;

/**
 * 打标签，ai打标签，数据库复用
 */

public interface TagServer {
    /**
     * 给一篇笔记打标签
     * @param content   笔记标题+内容（喂给ai）
     * @param knowledgeId  笔记id
     * @return
     */
    List<String> tag (String content,Long knowledgeId);

    /** 标签已由上游 AI 生成时，只负责复用/建标签并写关系，不再调用 AI */
    List<String> linkTags(List<String> tags, Long knowledgeId);
}
