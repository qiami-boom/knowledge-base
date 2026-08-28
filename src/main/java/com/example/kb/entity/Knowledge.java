package com.example.kb.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Knowledge {

    private Long id;

    private String title;

    private String content;

    private String source;

    private String category;//分类

    private String tags;//标签

    private Integer starRating;//收藏星级

    private Integer aiScore;//ai推荐分

    private String personalNote;//个人备注

    private LocalDateTime createdAt;//创建时间

    private LocalDateTime updatedAt;//更新时间

}
