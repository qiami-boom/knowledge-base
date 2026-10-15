package com.example.kb.service;

import com.example.kb.entity.Knowledge;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

    public int[] importFromJsonl(List<Knowledge> list) {
        int total = list.size();
        int scoredCount = 0;
        List<Knowledge> qualified = new ArrayList<>();

        for (Knowledge knowledge : list) {
            int score = aiService.scoreKnowledge(knowledge);
            knowledge.setAiScore(score);

            if (score > 0) scoredCount++;  // 评到分（不是失败的 0）

            // 只有分数 ≥ 最低要求 的才入库
            // 如果 AI 调用失败返回 0，我们也默认不入库（保守策略）
            if (score >= minScore) {
                qualified.add(knowledge);
            }
        }

        knowledgeService.batchAdd(qualified);

        // 返回统计数据：[总数, 成功评分数, 入库数]
        return new int[]{total, scoredCount, qualified.size()};
    }
}