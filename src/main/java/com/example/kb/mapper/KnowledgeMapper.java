package com.example.kb.mapper;

import com.example.kb.entity.Knowledge;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * knowledge 表的数据访问入口。
 * 所有读写方法都要求传入 userId，SQL 侧用 user_id 做归属过滤。
 */
@Mapper
public interface KnowledgeMapper {

    void insert(Knowledge knowledge);

    void batchInsert(List<Knowledge> list);

    Knowledge findById(@Param("id") Long id, @Param("userId") Long userId);

    List<Knowledge> findAll(@Param("userId") Long userId);

    void update(Knowledge knowledge);

    void deleteById(@Param("id") Long id, @Param("userId") Long userId);
}
