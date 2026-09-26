package com.example.kb.config;


import lombok.Data;
@Data 
public class Result<T> {
    private int code;
    private String message;
    private T data;

    //成功，不带数据时
    public static <T> Result<T> success(){
        return success(null);
    }

    //成功，带数据，前端让后端办事，事办成了返回数据告诉前端
    public static <T> Result<T> success(T data){
        Result<T> result = new Result<>();
        result.setData(data);
        result.setCode(200);
        result.setMessage("操作成功");
        return result;
    }

    //失败，带状态码和消息
    public static <T> Result<T> error(Integer code,String message){
        Result<T> result = new Result<>();
        result.setData(null);
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
}
