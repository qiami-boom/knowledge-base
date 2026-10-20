package com.example.kb.controller;

import com.example.kb.entity.Knowledge;
import com.example.kb.service.ImportService;
import com.example.kb.util.JsonlParser;
import com.example.kb.util.MdParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 导入功能接口，目前可对md. json.文件进行导入
 * 接收前端上传的文件再将其交给解析器和评分服务处理
 */
@RestController
@RequestMapping("/api/knowledge")
@CrossOrigin(origins = "*")
public class ImportController {

    @Autowired
    private ImportService importService;

    @PostMapping("/import")
    //@RequestParam("file") MultipartFile file → 接收文件，转成 MultipartFile 对象
    public Map<String, Object> importFile(@RequestParam("file") MultipartFile file) {
        try {
            // 按扩展名分流：.md 走完整对话解析器，其余走 JSONL 概要解析器
            String filename = file.getOriginalFilename();
            boolean isMarkdown = filename != null && filename.toLowerCase().endsWith(".md");

            List<Knowledge> list = isMarkdown
                    ? MdParser.parse(file.getInputStream())
                    : JsonlParser.parse(file.getInputStream());

            int[] result = importService.importFromJsonl(list);
            return Map.of(
                    "success", true,
                    "parser", isMarkdown ? "MdParser" : "JsonlParser", // 告诉前端实际用了哪个解析器
                    "total", result[0],        // 总记录数
                        "scored", result[1],       // AI 成功评分的数量
                        "imported", result[2]  );    // 实际入库（≥3 星）的数量);
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }
}
