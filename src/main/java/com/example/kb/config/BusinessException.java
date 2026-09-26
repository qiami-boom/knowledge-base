package com.example.kb.config;

import lombok.Getter;
@Getter
//这个类存在的意义，就是让"错误"从"返回值"里独立出来
public class BusinessException extends RuntimeException{
    private Integer code;

    public BusinessException(String message){
        super(message);
        this.code=500;
    }
    public BusinessException(Integer code,String message){
        super(message);
        this.code=code;
    }
}
