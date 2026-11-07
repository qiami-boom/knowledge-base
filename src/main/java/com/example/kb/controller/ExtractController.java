package com.example.kb.controller;

import com.example.kb.config.BusinessException;
import com.example.kb.config.Result;
import com.example.kb.service.ExtractService;
import com.example.kb.service.dto.ExtractRequest;
import com.example.kb.service.dto.ExtractResult;
import com.example.kb.service.dto.ExtractUrlRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/extract")
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

    /**
     * 将HTTP的URL转换成Java方法参数,接收一个网址返回一个草稿
     * @param request
     * @return
     */
    @PostMapping("/url")
    public Result<ExtractResult> extractUrl(@RequestBody ExtractUrlRequest request){
        if(request == null || request.url() == null || request.url().isBlank()){
            throw new BusinessException(400, "网址不能为空");
        }
        return Result.success(extractService.extractUrl(request.url()));
    }

    /**
     * “@RequestParam("file") MultipartFile file”表示从multipart/from-data中取名为file的不见
     * MultipartFile是Spring对上传文件的包装
     * @param file
     * @return
     * @throws IOException
     */
    @PostMapping("/file")
    public Result<ExtractResult> extractFile(@RequestParam("file") MultipartFile file) {
            if (file.isEmpty()) {
                throw new BusinessException(400, "上传文件不能为空");
            }
            try {
                return Result.success(extractService.extractFile(
                        file.getOriginalFilename(),  //拿文件名
                        file.getInputStream()   //拿内容流
                ));
            }catch (IOException e){
                throw new BusinessException(400, "文件读取失败", e);
            }
    }
}