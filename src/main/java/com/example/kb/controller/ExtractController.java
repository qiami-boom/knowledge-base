package com.example.kb.controller;

import com.example.kb.config.BusinessException;
import com.example.kb.config.Result;
import com.example.kb.service.ExtractService;
import com.example.kb.service.dto.ExtractRequest;
import com.example.kb.service.dto.ExtractResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/extract")
@CrossOrigin(origins = "*")
public class ExtractController {

    @Autowired
    private ExtractService extractService;

    @PostMapping
    public Result<ExtractResult> extract(@RequestBody ExtractRequest request) {
        if (request == null || request.text() == null || request.text().isBlank()) {
            throw new BusinessException(400, "待抽取的文本不能为空");
        }
        return Result.success(extractService.extract(request.text()));
    }

}