package com.example.kb.review;

public interface ReviewScheduler {
    ScheduleResult next(int currentStage,RecallQuality quality);
}
