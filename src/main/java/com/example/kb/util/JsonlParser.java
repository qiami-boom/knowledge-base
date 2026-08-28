package com.example.kb.util;

import com.example.kb.entity.Knowledge;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class JsonlParser {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static List<Knowledge> parse(InputStream inputStream) throws Exception{
        List<Knowledge> list = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))){

            String line;
            while ((line = reader.readLine())!=null){
                if(line.trim().isEmpty()) continue;
                JsonNode node = mapper.readTree(line);
                Knowledge knowledge = parseOne(node);
                list.add(knowledge);
            }
        }
        return list;
    }
    private static Knowledge parseOne(JsonNode node){
        Knowledge knowledge = new Knowledge();

        //intent->title 意图作为标题
        knowledge.setTitle(node.get("intent").asText());

        //learned 数组拼接 -> content(将知识点拼成内容)
        JsonNode learned = node.get("learned");
        if(learned != null && learned.isArray()){
            StringBuilder sb = new StringBuilder();
            for(int i = 0;i < learned.size(); i++){
                sb.append(".").append(learned.get(i).asText()).append("\n");
            }
            knowledge.setContent(sb.toString());
        }else{
            knowledge.setContent("");
        }

        // outcome → personalNote
        knowledge.setPersonalNote(
                node.get("outcome")!=null ? node.get("outcome").asText() :""
        );

        //固定值
        knowledge.setSource("TRAE_IMPORT");
        knowledge.setCategory("编程");
        knowledge.setTags("");
        knowledge.setStarRating(0);
        knowledge.setAiScore(0);

        //时间解析
        String timeStr = node.get("message_summary_time") != null
                ? node.get("message_summary_time").asText() : null;
        if (timeStr != null) {
            try {
                LocalDateTime time = LocalDateTime.parse(timeStr, formatter);
                knowledge.setCreatedAt(time);
                knowledge.setUpdatedAt(time);
            } catch (Exception e) {
                LocalDateTime now = LocalDateTime.now();
                knowledge.setCreatedAt(now);
                knowledge.setUpdatedAt(now);
            }
        } else {
            LocalDateTime now = LocalDateTime.now();
            knowledge.setCreatedAt(now);
            knowledge.setUpdatedAt(now);
        }
        return knowledge;
    }

}
