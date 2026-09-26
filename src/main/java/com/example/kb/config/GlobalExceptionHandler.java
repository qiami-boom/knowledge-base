package com.example.kb.config;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler (BusinessException.class)//声明专门接BusinessException异常
    public Result<Void> handleBusiness(BusinessException e){
        //业务异常:把真实的错误码code，和错误信息返回给前端
        return Result.error(e.getCode(),e.getMessage());
    }

    @ExceptionHandler (Exception.class)//声明接其他所有异常
    public Result<Void> handleSystem(Exception e){
        //系统异常:真实错误细节隐藏（对前端隐藏，后端打印错误信息并记日志）
        e.printStackTrace();
        return Result.error(500,"系统繁忙，稍后再试");
    }
}
