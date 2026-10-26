package com.example.kb.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 用户输入数据规范
 * @param title
 * @param content
 * @param source
 * @param category
 * @param tags
 * @param starRating
 * @param personalNote
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KnowledgeRequest(
    @NotBlank(message = "标题不能为空")
    @Size(max = 200,message = "标题不能超过200个字符")
    String title,

    @Size(max = 20000,message = "内容不能超过20000个字符")
    String content,

    @Size(max = 50,message = "来源不能超过50个字符")
    String source,

    @Size(max = 50,message = "分类不能超过50个字符")
    String category,

    @Size(max = 500,message = "标签不能超过500个字符")
    String tags,

    @Min(value = 0,message = "评分不能小于 0")
    @Max(value = 5,message = "评分不能大于 5")
    Integer starRating,

    @Size(max = 5000,message = "个人备注不能超过 5000 个字符")
    String personalNote
){

}
