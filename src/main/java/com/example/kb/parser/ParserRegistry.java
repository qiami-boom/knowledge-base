package com.example.kb.parser;

import java.util.Map;

/**
 * 这是注册表。它把“文件后缀”映射到“解析器”，是整个方案的枢纽。
 * 加`.txt` 以后只改它一行，`ImportService` 一个字都不用动。
 */
public final class ParserRegistry {
    private static final Map<String, DocumentParser> PARSERS = Map.of(
            "md",MdParser::parse,
            "jsonl",JsonlParser::parse,
            "docx",DocxParser::parse
    );

    //空构造器防止别人new
    private ParserRegistry(){
    }

    public static DocumentParser forFilename(String filename){
        if(filename == null || filename.isBlank()){
            return null;
        }

        String lowerName = filename.toLowerCase();
        int dot = lowerName.lastIndexOf('.');//找到最后一个点出现的位置
        if(dot < 0 || dot == lowerName.length() - 1){   //因为下标从0开始，且'.'也算作一个字符所以-1才是真正的最后一个'.'的位置
            return null;
        }
        String ext = lowerName.substring(dot + 1);//从点的位置后一位开始截取字串
        return PARSERS.get(ext);//将文件后缀名给键，按键取值，返回对应的解析器
    }
}
