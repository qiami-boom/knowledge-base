package com.example.kb.review;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper 
public interface ReviewCardMapper {
    
    void insert(ReviewCard reviewCard);

    List<ReviewCard> findDue(LocalDateTime now);

    List<ReviewCard> findByKnowledgeId(Long knowledgeId);

    ReviewCard findById(Long id);

    void updateSchedule(ReviewCard reviewCard);
}
