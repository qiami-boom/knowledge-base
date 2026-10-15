package com.example.kb.review;

import java.time.LocalDateTime;
import com.example.kb.config.BusinessException;
import java.util.List;
import org.springframework.stereotype.Service;


@Service 
public class ReviewService {
    
    private final ReviewCardMapper reviewCardMapper;
    private final ReviewScheduler reviewScheduler;

    public ReviewService(ReviewCardMapper reviewCardMapper, ReviewScheduler reviewScheduler){
        this.reviewCardMapper = reviewCardMapper;
        this.reviewScheduler = reviewScheduler;
    }

    public List<ReviewCard> dueCards(){
        LocalDateTime now = LocalDateTime.now();
        return reviewCardMapper.findDue(now);
    }
    
    public ReviewCard review(Long cardId, RecallQuality quality){
        ReviewCard reviewCard = reviewCardMapper.findById(cardId);
        if(reviewCard == null){
            throw new BusinessException(404, "复习卡不存在");
        }
        ScheduleResult result = reviewScheduler.next(reviewCard.getStage(),quality);
        int nextStage = result.stage();
        int intervalDays = result.intervalDays();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextReviewAt = now.plusDays(intervalDays);
        reviewCard.setStage(nextStage);
        reviewCard.setNextReviewAt(nextReviewAt);
        reviewCardMapper.updateSchedule(reviewCard);
        return reviewCard;
    }
}
