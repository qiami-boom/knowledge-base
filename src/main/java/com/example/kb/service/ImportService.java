package com.example.kb.service;

import com.example.kb.config.BusinessException;
import com.example.kb.entity.Knowledge;
import com.example.kb.parser.DocumentParser;
import com.example.kb.parser.ParserRegistry;
import com.example.kb.service.dto.ImportResult;
import com.example.kb.service.dto.ScoreResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ImportService {
    @Autowired
    private KnowledgeService knowledgeService;

    @Autowired
    private AiService aiService;

    @Value("${ai.import.min-score:3}")
    private int minScore;


    /**
     * 设置评分？todo 写注释
     * @param filename
     * @param inputStream
     * @param userId
     * @return
     */

    public ImportResult importFile(String filename, InputStream inputStream,Long userId) {
        List<Knowledge> parsed = parse(filename, inputStream);
        List<Knowledge> candidates = new ArrayList<>();
        List<ScoreResult> candidateResults = new ArrayList<>();

        int total = parsed.size();
        int scoredCount = 0;
        List<Knowledge> qualified = new ArrayList<>();
        //设置ai评分，记录ai成功评分知识的数量，设置知识标题，记录大于最低分数阈值的成功被ai入库的价值知识

        String fallbackTitle = filename.substring(0, filename.lastIndexOf('.'));
        //拿文件名暂时兜底没有标题的知识，用作ai评分标准,后续评分完会被替代

        for (Knowledge knowledge : parsed) {
            if(knowledge.getTitle() == null || knowledge.getTitle().isBlank()){
                knowledge.setTitle(fallbackTitle);
            }

            ScoreResult result = aiService.scoreKnowledge(knowledge);
            knowledge.setAiScore(result.score());
            knowledge.setSummary(result.summary());

            if (!"UNKNOWN".equals(result.category())) {
                scoredCount++;   // AI 真的返回了结果（哪怕是 LOGISTICS），才算“成功评分”
            }
            if("KNOWLEDGE".equals(result.category())){
                candidates.add(knowledge);
                candidateResults.add(result);
            }

            String title = result.title();
            if (title != null && !title.isBlank()) {
                knowledge.setTitle(title.length() > 40 ? title.substring(0, 40) : title);
            }
            log.info("[导入] category={}, score={}, why={}, general={}, title={}, reason={}",
                    result.category(), result.score(), result.why(), result.general(),
                    knowledge.getTitle(), result.reason());//日志,
        }
        // 第二趟：把这一批 KNOWLEDGE 交给 AI 排序，只让前 20% 保留 5 星
        if (!candidates.isEmpty()) {
            int topN = Math.max(3, (int) Math.ceil(candidates.size() * 0.2));//Math.ceil()向上取整
            List<Integer> picks = aiService.pickTopPrinciples(
                    candidates.stream().map(Knowledge::getTitle).toList(), topN);

            // picks 为空 = 排序调用失败，这时保持原分数；不要把 5 星全降成 4 星
            if (!picks.isEmpty()) {
                for (int i = 0; i < candidateResults.size(); i++) {
                    if (candidateResults.get(i).score() == 5 && !picks.contains(i + 1)) {
                        candidates.get(i).setAiScore(4);
                    }
                }
            }
        }

        // 第三阶段：降级做完后再决定谁入库
        for (Knowledge knowledge : candidates) {
            if (knowledge.getAiScore() >= minScore) {
                qualified.add(knowledge);
            }
        }
        if(!qualified.isEmpty()){
            knowledgeService.batchAdd(qualified,userId);
        }
        // 返回导入统计结果
        return new ImportResult(total, scoredCount, qualified.size());
    }

    private List<Knowledge> parse(String filename, InputStream inputStream){
        if(filename == null || filename.isBlank()){
            throw new BusinessException(400,"缺少文件名");
        }

        DocumentParser parser = ParserRegistry.forFilename(filename);
        if(parser == null){
            throw new BusinessException(400,"仅支持 .md / .jsonl / .docx 文件");
        }

        try{
            return parser.parse(inputStream);
        } catch(IOException e){
            //把技术异常转为业务异常，并保留cause
            throw new BusinessException(400,"文件无法解析",e);
        }
    }
}
