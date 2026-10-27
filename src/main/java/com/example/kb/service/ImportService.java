package com.example.kb.service;

import com.example.kb.config.BusinessException;
import com.example.kb.entity.Knowledge;
import com.example.kb.service.dto.ImportResult;
import com.example.kb.util.JsonlParser;
import com.example.kb.util.MdParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class ImportService {
    @Autowired
    private KnowledgeService knowledgeService;

    @Autowired
    private AiService aiService;

    @Value("${ai.import.min-score:3}")
    private int minScore;

    public ImportResult importFile(String filename, InputStream inputStream) {
        List<Knowledge> parsed = parse(filename, inputStream);

        int total = parsed.size();
        int scoredCount = 0;
        List<Knowledge> qualified = new ArrayList<>();

        for (Knowledge knowledge : parsed) {
            int score = aiService.scoreKnowledge(knowledge);
            knowledge.setAiScore(score);

            if (score > 0) scoredCount++;  // 评到分（不是失败的 0）

            // 只有分数 ≥ 最低要求 的才入库
            // 如果 AI 调用失败返回 0，我们也默认不入库（保守策略）
            if (score >= minScore) {
                qualified.add(knowledge);
            }
        }
        if(!qualified.isEmpty()){
            knowledgeService.batchAdd(qualified);
        }

        // 返回导入统计结果
        return new ImportResult(total, scoredCount, qualified.size());
    }

    private List<Knowledge> parse(String filename, InputStream inputStream){
        if(filename == null){
            throw new BusinessException(400,"缺少文件名");
        }

        String lowerName = filename.toLowerCase();//生成一个小写文件名

        try{
            if(lowerName.endsWith(".md")){
                return MdParser.parse(inputStream);
            }
            if(lowerName.endsWith(".jsonl")){
                return JsonlParser.parse(inputStream);
            }
            throw new BusinessException(400,"仅支持 .md和 .jsonl文件");
        } catch(IOException e){
            //把技术异常转为业务异常，并保留cause
            throw new BusinessException(400,"文件无法解析",e);
        }
    }
}
