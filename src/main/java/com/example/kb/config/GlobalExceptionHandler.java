package com.example.kb.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler (BusinessException.class)//声明专门接BusinessException异常
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e){
        log.warn("业务异常：code={},message={}",e.getCode(),e.getMessage());
        return ResponseEntity
                .status(e.getCode())//业务异常用e.getCode()设置HTTP状态
                .body(Result.error(e.getCode(),e.getMessage()));
    }

    @ExceptionHandler (Exception.class)
    public ResponseEntity<Result<Void>> handleSystem(Exception e){
        log.error("系统异常",e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(500,"系统繁忙，稍后再试"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(
            MethodArgumentNotValidException e){

        String message = e.getBindingResult()//拿到本次参数校验的结果
                .getFieldErrors() //拿到所有字段错误
                .stream().findFirst()//当前只展示一个错误，避免一次弹出一长串
                .map(error -> error.getDefaultMessage())//取出注解中配置的message
                .orElse("请求参数不合法");

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, message));
    }
}
