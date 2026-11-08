package com.example.kb.service.dto;

/**
 * 接ai返回的单条知识点草稿
 * why / general 用 Boolean,缺字段时是 null，好校验
 * @param title
 * @param content
 * @param reason
 * @param why
 * @param general
 */
public record ExtractItem (String title,String content,String reason,Boolean why,Boolean general,String summary){
}
