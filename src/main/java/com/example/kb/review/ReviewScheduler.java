package com.example.kb.review;

/**
 * 复习调度接口 —— 策略模式的"插座"
 * 串联：上游 ReviewService 只依赖本接口，不认识任何具体算法（依赖倒置）
 *      下游每个实现类是一种算法，目前唯一实现是 FixedLadderScheduler
 * 将来换 SM-2/FSRS：只加新实现类，Service 及以上零改动（开闭原则）
 */
public interface ReviewScheduler {
    ScheduleResult next(int currentStage,RecallQuality quality);
}
