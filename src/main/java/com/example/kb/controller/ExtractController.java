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
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;

@RestController
@RequestMapping("/api/extract")
public class ExtractController {

    @Autowired
    private ExtractService extractService;

    @PostMapping
    public Result<ExtractResult> extract(@RequestBody ExtractRequest request,
                                         HttpServletRequest httpRequest) {
        if (request == null || request.text() == null || request.text().isBlank()) {
            throw new BusinessException(400, "待抽取的文本不能为空");
        }
        Long userId = (Long) httpRequest.getAttribute("userId");
        return Result.success(extractService.extract(request.text(), userId));
    }

    /**
     * 将HTTP的URL转换成Java方法参数,接收一个网址返回一个草稿
     * @param request
     * @return
     */
    @PostMapping("/url")
    public Result<ExtractResult> extractUrl(@RequestBody ExtractUrlRequest request,
                                            HttpServletRequest httpRequest){
        if(request == null || request.url() == null || request.url().isBlank()){
            throw new BusinessException(400, "网址不能为空");
        }
        Long userId = (Long) httpRequest.getAttribute("userId");
        return Result.success(extractService.extractUrl(request.url(), userId));
    }

    /**
     * “@RequestParam("file") MultipartFile file”表示从multipart/from-data中取名为file的不见
     * MultipartFile是Spring对上传文件的包装
     * @param file
     * @return
     * @throws IOException
     */
    @PostMapping("/file")
    public Result<ExtractResult> extractFile(@RequestParam("file") MultipartFile file,
                                             HttpServletRequest httpRequest) {
        if (file.isEmpty()) {
            throw new BusinessException(400, "上传文件不能为空");
        }
        Long userId = (Long) httpRequest.getAttribute("userId");
        try {
            return Result.success(extractService.extractFile(
                    file.getOriginalFilename(),
                    file.getInputStream(),
                    userId
            ));
        }catch (IOException e){
            throw new BusinessException(400, "文件读取失败", e);
        }
    }
}