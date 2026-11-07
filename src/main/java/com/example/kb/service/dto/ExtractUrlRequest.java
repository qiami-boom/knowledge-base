package com.example.kb.service.dto;

/**
 * 网页抓取的请求体：只有网址一个字段
 */
public record ExtractUrlRequest(String url) {
}
