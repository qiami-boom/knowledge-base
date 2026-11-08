package com.example.kb.service;

import com.example.kb.config.BusinessException;
import com.example.kb.entity.Knowledge;
import com.example.kb.parser.DocumentParser;
import com.example.kb.parser.ParserRegistry;
import com.example.kb.service.dto.ExtractItem;
import com.example.kb.service.dto.ExtractResult;
import com.example.kb.service.dto.ScoreResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ExtractService {

    @Autowired
    private AiService aiService;

    @Autowired
    private KnowledgeService knowledgeService;

    public ExtractResult extract(String text,Long userId) {
        List<ExtractItem> items = aiService.extract(text);
        if (items.isEmpty()) {
            return new ExtractResult(null, List.of());
        }
        String batchId = UUID.randomUUID().toString();  //生成UUID识别码,给”一次抽取“盖一个批次章,方便日后按批次操作
        List<Knowledge> drafts = new ArrayList<>();
        return saveDrafts(toDrafts(items, batchId),batchId,userId);
    }

    private List<Knowledge> toDrafts(List<ExtractItem> items, String batchId) {
        List<Knowledge> drafts = new ArrayList<>();
        for (ExtractItem item : items) {
            Knowledge knowledge = new Knowledge();
            knowledge.setTitle(item.title());
            knowledge.setContent(item.content());
            knowledge.setSource("AGENT_EXTRACT");
            knowledge.setStatus("DRAFT");
            knowledge.setBatchId(batchId);
            knowledge.setAiScore(ScoreResult.scoreOf(item.why(), item.general()));
            knowledge.setSummary(item.summary());
            drafts.add(knowledge);
        }
        return drafts;
    }

    private ExtractResult saveDrafts(List<Knowledge> drafts, String batchId,Long userId) {
        knowledgeService.addDrafts(drafts,userId);

        List<ExtractResult.Item> views = new ArrayList<>();
        for (Knowledge knowledge : drafts) {
            views.add(new ExtractResult.Item(
                    knowledge.getId(),
                    knowledge.getTitle(),
                    knowledge.getContent(),
                    knowledge.getAiScore()
            ));
        }

        return new ExtractResult(batchId, views);
    }

    /** 网页抓取：正文当一整段文本，直接复用 extract */
    public ExtractResult extractUrl(String url,Long userId) {
        if (url == null || url.isBlank()) {
            throw new BusinessException(400, "网址不能为空");
        }
        AiService.WebPage page = aiService.fetchUrl(url);
        if (page == null || page.content() == null || page.content().isBlank()) {
            throw new BusinessException(400, "这个网址抓不到正文，可能需要登录、或者是图片站");
        }
        return extract(page.content(),userId);
    }

    /** 文件：按对应解析器分好条，逐条评分转草稿，不再让 AI 抽一遍 */
    public ExtractResult extractFile(String filename, InputStream inputStream,Long userId) {
        DocumentParser parser = ParserRegistry.forFilename(filename);
        if (parser == null) {
            throw new BusinessException(400, "仅支持 .md / .jsonl / .docx 文件");
        }

        List<Knowledge> parsed;
        try {
            parsed = parser.parse(inputStream);
        } catch (IOException e) {
            throw new BusinessException(400, "文件无法解析", e);
        }
        if (parsed.isEmpty()) {
            throw new BusinessException(400, "文件里没有解析出内容");
        }

        String batchId = UUID.randomUUID().toString();
        String fallbackTitle = filename.substring(0, filename.lastIndexOf('.'));
        List<Knowledge> drafts = new ArrayList<>();

        for (Knowledge k : parsed) {
            if (k.getTitle() == null || k.getTitle().isBlank()) {
                k.setTitle(fallbackTitle);
            }
            ScoreResult r = aiService.scoreKnowledge(k);
            if ("UNKNOWN".equals(r.category())) {
                continue;
            }
            k.setAiScore(r.score());
            k.setSummary(r.summary());
            if (r.title() != null && !r.title().isBlank()) {
                k.setTitle(r.title().length() > 40 ? r.title().substring(0, 40) : r.title());
            }
            k.setStatus("DRAFT");
            k.setBatchId(batchId);
            drafts.add(k);
        }
        return saveDrafts(drafts, batchId,userId);
    }
}
