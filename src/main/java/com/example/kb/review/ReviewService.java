package com.example.kb.review;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    public List<ReviewCard> dueCards(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        return reviewCardMapper.findDue(userId, now);
    }

    /** 已经复习过的卡，最近复习的排前面 */
    public List<ReviewCard> reviewedCards(Long userId, int limit) {
        return reviewCardMapper.findReviewed(userId, limit);
    }

    /**
     * 五段掌握现状：把 1~7 阶压成四段计数，外加总卡数。
     * stage 是"阶梯位置"，不是掌握程度——所以这里只呈现事实分布，不合成百分比。
     */
    public Map<String, Integer> stageStats(Long userId) {
        List<Map<String, Object>> raw = reviewCardMapper.countByStage(userId);

        int initial = 0;      // 1-2 阶
        int strengthen = 0;   // 3-4 阶
        int consolidate = 0;  // 5-6 阶
        int mastered = 0;     // 7 阶

        for (Map<String, Object> row : raw) {
            int stage = ((Number) row.get("stage")).intValue();
            int count = ((Number) row.get("cnt")).intValue();
            if (stage <= 2) {
                initial += count;
            } else if (stage <= 4) {
                strengthen += count;
            } else if (stage <= 6) {
                consolidate += count;
            } else {
                mastered += count;
            }
        }

        Map<String, Integer> stats = new LinkedHashMap<>();
        stats.put("initial", initial);
        stats.put("strengthen", strengthen);
        stats.put("consolidate", consolidate);
        stats.put("mastered", mastered);
        stats.put("total", initial + strengthen + consolidate + mastered);
        return stats;
    }

    /**
     * 每条知识的最高复习阶数：knowledgeId -> maxStage。
     * 列表里用它显示"这条复习到哪一阶了"，没有卡的知识不会出现在 Map 里。
     */
    public Map<Long, Integer> progressMap(Long userId) {
        List<Map<String, Object>> raw = reviewCardMapper.maxStageByKnowledge(userId);
        Map<Long, Integer> result = new HashMap<>();
        for (Map<String, Object> row : raw) {
            Long knowledgeId = ((Number) row.get("knowledgeId")).longValue();
            int maxStage = ((Number) row.get("maxStage")).intValue();
            result.put(knowledgeId, maxStage);
        }
        return result;
    }

    /**
     * 为某张复习卡实时生成一道选择题。
     * 先校验卡归属，再取它对应的知识原文交给 AI 出题；卡片不存在返回 null，由 Controller 转 404。
     */
    public ChoiceQuestion generateQuiz(Long cardId, Long userId) {
        ReviewCard card = reviewCardMapper.findById(cardId, userId);
        if (card == null) {
            return null;
        }
        Knowledge knowledge = knowledgeService.findById(card.getKnowledgeId(), userId);
        return aiService.generateChoiceQuestion(knowledge);
    }

    public ReviewCard review(Long cardId, RecallQuality quality, Long userId) {
        ReviewCard reviewCard = reviewCardMapper.findById(cardId, userId);
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
    public List<ReviewCard> createCards(Long knowledgeId, Long userId) {
        // 先校验知识归属：不属于当前用户时直接 404，
        // 不能先查已有卡片，否则会通过幂等路径把别人的卡片返回出去
        Knowledge knowledge = knowledgeService.findById(knowledgeId, userId);

        List<ReviewCard> existing = reviewCardMapper.findByKnowledgeId(knowledgeId, userId);
        if (!existing.isEmpty()) {
            return existing;
        }
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