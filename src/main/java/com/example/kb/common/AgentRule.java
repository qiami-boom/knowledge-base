package com.example.kb.common;

/**
 * AI 回答风格（用户偏好）
 * 只是"选项标签"，具体行为由各处的 switch 拼 prompt 决定
 *
 * 库里存的是枚举名（name()），如 "EXPLAIN"，不是中文。
 * 不要改成中文长句：以后改名、做多语言时，存量数据会全部作废。
 */
public enum AgentRule {

    EXPLAIN,   // 讲解模式：像老师上课，逐步讲清原理并举例（默认）
    EXAM,      // 考试模式：只出题不给答案，检验记忆
    SOCRATIC,  // 苏格拉底模式：只反问不直接给结论，逼你自己想
    CUSTOM     // 自定义模式：用用户自己写的规则，兜底通道

}
