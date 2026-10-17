package com.example.kb.review;

import java.time.LocalDateTime;
import com.example.kb.config.BusinessException;
import java.util.List;
import org.springframework.stereotype.Service;


/**
 * 复习服务 —— 整条复习功能线的编排者（领班）
 * 串联：ReviewController 调它 → 它读库（findById / findDue）
 *      → 把当前阶数和自评交给算法（reviewScheduler.next）→ 把结果写回库（updateSchedule）
 * 职责边界：不写 SQL、不算阶梯天数 —— 换算法或换数据库，本文件一行不改（开闭原则）
 */
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
        //把当前阶数和自评交给调度算法，换回新阶段和间隔天数
        ScheduleResult result = reviewScheduler.next(reviewCard.getStage(),quality);
        int nextStage = result.stage();
        int intervalDays = result.intervalDays();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextReviewAt = now.plusDays(intervalDays);
        reviewCard.setStage(nextStage);
        reviewCard.setNextReviewAt(nextReviewAt);
        reviewCard.setLastReviewedAt(now);
        reviewCardMapper.updateSchedule(reviewCard);
        return reviewCard;
    }
}
