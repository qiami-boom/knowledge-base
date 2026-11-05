package com.example.kb.service;

import com.example.kb.entity.Knowledge;
import com.example.kb.service.dto.ExtractItem;
import com.example.kb.service.dto.ExtractResult;
import com.example.kb.service.dto.ScoreResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ExtractService {

    @Autowired
    private AiService aiService;

    @Autowired
    private KnowledgeService knowledgeService;

    public ExtractResult extract(String text){
        List<ExtractItem> items = aiService.extract(text);
        if(items.isEmpty()){
            return new ExtractResult(null, List.of());
        }

        String batchId = UUID.randomUUID().toString();  //生成UUID识别码,给”一次抽取“盖一个批次章,方便日后按批次操作
        List<Knowledge> drafts = new ArrayList<>();

        for(ExtractItem item : items){
            Knowledge knowledge = new Knowledge();
            knowledge.setTitle(item.title());
            knowledge.setContent(item.content());
            knowledge.setSource("AGENT_EXTRACT");
            knowledge.setStatus("DRAFT");
            knowledge.setBatchId(batchId);
            knowledge.setAiScore(ScoreResult.scoreOf(item.why(),item.general()));
            drafts.add(knowledge);
        }

        knowledgeService.addDrafts(drafts);

        List<ExtractResult.Item> views = new ArrayList<>();
        for(Knowledge knowledge : drafts){
            views.add(new ExtractResult.Item(
                    knowledge.getId(),
                    knowledge.getTitle(),
                    knowledge.getContent(),
                    knowledge.getAiScore()
            ));
        }

        return new ExtractResult(batchId, views);
    }
}
