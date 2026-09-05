package com.example.kb.controller;

import com.example.kb.entity.Knowledge;
import com.example.kb.service.ImportService;
import com.example.kb.util.JsonlParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
@CrossOrigin(origins = "*")
public class ImportController {

    @Autowired
    private ImportService importService;

    @PostMapping("/import")
    //@RequestParam("file") MultipartFile file → 接收文件，转成 MultipartFile 对象
    public Map<String, Object> importJsonl(@RequestParam("file") MultipartFile file) {
        try {
            List<Knowledge> list = JsonlParser.parse(file.getInputStream());
            int[] result= importService.importFromJsonl(list);
            return Map.of(
                    "success", true,
                    "total", result[0],        // 总记录数
                        "scored", result[1],       // AI 成功评分的数量
                        "imported", result[2]  );    // 实际入库（≥3 星）的数量);
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }
}