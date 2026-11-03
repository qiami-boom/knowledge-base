package com.example.kb.entity;

import com.example.kb.common.Timestamped;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
/**
 * 知识库知识
 */
public class Knowledge implements Timestamped {

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

    private String summary;//一句话摘要，agent 检索只读它

    private String status = "ACTIVE";//状态:DRAFT 草稿/ACTIVE 生效

    private Long userId;//归属用户

    @Override
    public void stamp(LocalDateTime now) {
        if (createdAt == null) {
            createdAt = now;   //解析器已从文件里读到原始时间，不覆盖
        }
        updatedAt = now;   //每次写入都刷新
    }
}
