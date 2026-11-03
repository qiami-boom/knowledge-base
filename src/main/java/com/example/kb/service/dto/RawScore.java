package com.example.kb.service.dto;

/**
 * AI 原始回答：只让模型答两个是非题，分数由后端算
 * 用 Boolean 而不是 boolean，是为了区分"模型没给这个字段"和"模型明确答了 false"
 */
public record RawScore(String category, Boolean why, Boolean general, String title, String reason) {
}