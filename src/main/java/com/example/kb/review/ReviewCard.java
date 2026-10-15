package com.example.kb.review;
import lombok.Data;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data 
@AllArgsConstructor 
@NoArgsConstructor 
public class ReviewCard {

    private Long id;

    private Long knowledgeId;

    private String question;

    private String answer;

    private Integer stage;

    private LocalDateTime nextReviewAt;//下次复习时间

    private LocalDateTime lastReviewedAt;//最近一次复习时间

    private LocalDateTime createdAt;//卡片生成时间
}
