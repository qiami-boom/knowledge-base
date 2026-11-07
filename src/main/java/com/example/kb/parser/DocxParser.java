package com.example.kb.parser;

import com.example.kb.entity.Knowledge;
import org.apache.poi.xwpf.usermodel.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 实现类，用Apache POI读Word文件
 */
public class DocxParser {

    public static final int MAX_CONTENT_LENGTH = 15000;

    public static List<Knowledge> parse(InputStream inputStream) throws IOException{
        try(XWPFDocument doc = new XWPFDocument(inputStream)){
            List<String> parts = new ArrayList<>();

            for(IBodyElement element : doc.getBodyElements()){
                if(element instanceof XWPFParagraph p){   //如果element实际是XWPFParagraph,就把它赋给p
                    String text = p.getText();
                    if(text == null || text.isBlank()){
                        continue;   //空段落直接跳过
                    }
                    parts.add(text.trim());
                }else if(element instanceof XWPFTable t){   //如果element实际是XWPFTable，就把它赋给t，第一层：找到表格
                    for(XWPFTableRow row : t.getRows()){   //第二层：遍历行
                        List<String> cells = new ArrayList<>();   //每次循环创建一个空列表，行与行之间互不干扰
                        for(XWPFTableCell cell : row.getTableCells()){   //第三层：遍历格子
                            //先取文字，再把里面的换行替换成空格最后去首尾空白
                            cells.add(cell.getText().replace("\n"," ").trim());
                        }
                        parts.add(String.join(" | ",cells));   //拼成一行的文本
                    }
                }
            }
            String content = String.join("\n",parts);  //将所有字符串用'/n'拼接成一个字符串,parts作为数组是被拼接的合集
            if(content.length() > MAX_CONTENT_LENGTH){
                content = content.substring(0,MAX_CONTENT_LENGTH);
            }
            Knowledge knowledge = new Knowledge();
            knowledge.setContent(content);
            knowledge.setSource("DOCX_IMPORT");
            knowledge.setCategory("");
            knowledge.setStarRating(0);
            knowledge.setAiScore(0);
            return List.of(knowledge);
        }catch (IOException e){
            throw e;
        }catch (Exception e){
            throw new IOException("不是有效的 docx 文件",e);
        }
    }


}
