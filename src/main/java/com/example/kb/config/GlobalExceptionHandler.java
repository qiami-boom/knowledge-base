package com.example.kb.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    // 参数类型不匹配：比如 ?quality=HELLO 这种枚举传错
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException e){
        log.warn("参数类型错误: name={},value={}", e.getName(), e.getValue());
        return ResponseEntity.badRequest()
                .body(Result.error(400,"参数" + e.getName() + " 取值不合法"));
    }

    // 缺少必填请求参数：比如连 ?quality 都没带
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少必填参数：{}", e.getParameterName());
        return ResponseEntity.badRequest()
                .body(Result.error(400, "缺少必填参数：" + e.getParameterName()));
    }

    // URL 没有匹配到任何接口（拼错路径时走这里，而不是“系统繁忙”）
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResource(NoResourceFoundException e) {
        log.warn("接口不存在：{}", e.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "接口不存在"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleBadBody(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败：{}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(Result.error(400, "请求体格式不正确"));
    }
}
