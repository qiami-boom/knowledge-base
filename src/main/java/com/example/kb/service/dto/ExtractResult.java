package com.example.kb.service.dto;

import java.util.List;

/**
 * 一次抽取的结果：一个批次的结果 + 这批草稿的视图，给前端的数据
 */
public record ExtractResult (String batchId, List<Item> items){

    /**只传给前端用的上的字段,因为Item只在“抽取结果"的时候有意义,放在嵌套里少建一个文件*/
    public record Item(Long id, String title, String content, Integer aiScore){
    }
}
