package com.example.kb.review;

import java.time.LocalDateTime;
import java.util.List;

import com.example.kb.config.BusinessException;
import com.example.kb.entity.Knowledge;
import com.example.kb.service.AiService;
import com.example.kb.service.KnowledgeService;
import org.springframework.stereotype.Service;

/**
 * 复习服务 —— 整条复习功能线的编排者（领班）
 * 串联：ReviewController 调它 → 它读库（findById / findDue）
 *      → 把当前阶数和自评交给算法（reviewScheduler.next）→ 把结果写回库（updateSchedule）
 * 造卡：createCards 去找 KnowledgeService 要原文、找 AiService 要题目，再写进 review_card
 * 职责边界：不写 SQL、不算阶梯天数 —— 换算法或换数据库，本文件一行不改（开闭原则）
 */
@Service
public class ReviewService {

    private final ReviewCardMapper reviewCardMapper;
    private final ReviewScheduler reviewScheduler;
    private final KnowledgeService knowledgeService;
    private final AiService aiService;

    public ReviewService(ReviewCardMapper reviewCardMapper,
                         ReviewScheduler reviewScheduler,
                         KnowledgeService knowledgeService,
                         AiService aiService) {
        this.reviewCardMapper = reviewCardMapper;
        this.reviewScheduler = reviewScheduler;
        this.knowledgeService = knowledgeService;
        this.aiService = aiService;
    }

    public List<ReviewCard> dueCards() {
        LocalDateTime now = LocalDateTime.now();
        return reviewCardMapper.findDue(now);
    }

    public ReviewCard review(Long cardId, RecallQuality quality) {
        ReviewCard reviewCard = reviewCardMapper.findById(cardId);
        if (reviewCard == null) {
            throw new BusinessException(404, "复习卡不存在");
        }
        //把当前阶数和自评交给调度算法，换回新阶段和间隔天数
        ScheduleResult result = reviewScheduler.next(reviewCard.getStage(), quality);
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

    /**
     * 为一条知识生成复习卡
     * 幂等（idempotent）：已有卡就直接返回，不重复调 AI —— AI 慢且花钱
     */
    public List<ReviewCard> createCards(Long knowledgeId) {
        List<ReviewCard> existing = reviewCardMapper.findByKnowledgeId(knowledgeId);
        if (!existing.isEmpty()) {
            return existing;
        }

        Knowledge knowledge = knowledgeService.findById(knowledgeId); // 不存在会抛 404
        QuizQuestion quiz = aiService.generateQuizQuestion(knowledge);

        // AI 失败时返回的是空串，白卡不能入库
        if (quiz.question() == null || quiz.question().isBlank()
                || quiz.answer() == null || quiz.answer().isBlank()) {
            throw new BusinessException(502, "AI 出题失败，请稍后重试");
        }

        ReviewCard card = new ReviewCard();
        card.setKnowledgeId(knowledgeId);
        card.setQuestion(quiz.question());
        card.setAnswer(quiz.answer());
        card.setStage(1);
        LocalDateTime now = LocalDateTime.now();
        card.setNextReviewAt(now);      // 立即到期
        card.setLastReviewedAt(null);   // 从未复习
        card.setCreatedAt(now);
        reviewCardMapper.insert(card);

        return List.of(card);
    }
}