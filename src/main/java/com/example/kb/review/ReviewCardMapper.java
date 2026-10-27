package com.example.kb.review;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * 复习卡数据访问接口 —— 唯一碰数据库的层
 * 串联：ReviewService 调它 → SQL 本体在 resources/mapper/ReviewCardMapper.xml（namespace 指向本接口）
 */
@Mapper
public interface ReviewCardMapper {
    
    void insert(ReviewCard reviewCard);

    List<ReviewCard> findDue(LocalDateTime now);

    List<ReviewCard> findByKnowledgeId(Long knowledgeId);

    ReviewCard findById(Long id);

    void updateSchedule(ReviewCard reviewCard);
}
