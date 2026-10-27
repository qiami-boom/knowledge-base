package com.example.kb.controller;

import com.example.kb.config.BusinessException;
import com.example.kb.config.Result;
import com.example.kb.service.ImportService;
import com.example.kb.service.dto.ImportResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

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
    public Result<ImportResult> importFile(
            @RequestParam("file") MultipartFile file) throws IOException {

        if(file.isEmpty()){
            throw new BusinessException(400,"上传文件不能为空");
        }

        ImportResult result = importService.importFile(
                file.getOriginalFilename(),
                file.getInputStream()
        );

        return Result.success(result);
    }
}
