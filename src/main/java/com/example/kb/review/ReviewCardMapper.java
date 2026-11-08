package com.example.kb.review;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 复习卡数据访问接口 —— 唯一碰数据库的层
 * 串联：ReviewService 调它 → SQL 本体在 resources/mapper/ReviewCardMapper.xml（namespace 指向本接口）
 * 所有查询都要求 userId：review_card 自身没有 user_id，靠 knowledge_id 连表过滤。
 */
@Mapper
public interface ReviewCardMapper {

    void insert(ReviewCard reviewCard);

    List<ReviewCard> findDue(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    List<ReviewCard> findByKnowledgeId(@Param("knowledgeId") Long knowledgeId,
                                       @Param("userId") Long userId);

    ReviewCard findById(@Param("id") Long id, @Param("userId") Long userId);

    void updateSchedule(ReviewCard reviewCard);
}
