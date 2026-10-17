package com.example.kb.knowledge_tag.mapper;

import com.example.kb.knowledge_tag.commom.Tag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TagMapper {

    /** L0：按规范化名精确查 */
    Tag findByNormalized(@Param("normalized") String normalized);

    /** L1：按别名规范化名查 */
    Tag findByAliasNormalized(@Param("normalized") String normalized);

    /** 新建标签（自增 id 回填到入参对象） */
    void insertTag(Tag tag);

    /** 被复用一次，usage_count + 1 */
    void increaseUsage(@Param("tagId") Long tagId);

    /** 记录 笔记↔标签 关联（重复关联自动忽略） */
    void link(@Param("knowledgeId") Long knowledgeId, @Param("tagId") Long tagId);
}