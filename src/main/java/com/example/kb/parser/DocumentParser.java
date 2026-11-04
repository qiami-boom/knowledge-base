package com.example.kb.parser;

import com.example.kb.entity.Knowledge;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 这是契约。它定义“任何解析器必须长什么样”，本身不做任何事
 */
@FunctionalInterface
public interface DocumentParser {
    List<Knowledge> parse(InputStream inputStream) throws IOException;
}
